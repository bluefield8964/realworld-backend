# Commerce Payment Module

## Module Responsibilities Overview
- `PaymentController` accepts payment-create requests and delegates orchestration.
- `OrderService` creates or reuses an order, creates Stripe checkout session, and persists order transitions.
- `PaymentService` maintains local payment ledger states (`INIT`, `PAYING`, `FAILED`, etc.).
- `WebhookController` maps internal error categories to Stripe-facing HTTP responses.
- `WebhookService` handles Stripe callbacks with type filtering, event idempotency, and state transitions.
- `StripeEventService` stores webhook event lifecycle (`PROCESSING`, `SUCCEEDED`, `FAILED`, `DEAD`) for retry control.
- `AbnormalOrderService` stores and reconciles terminal/exhausted abnormal cases.
- `AbnormalOrderReconcileJob` periodically triggers abnormal-order reconciliation.
- `OrderReconcileService` is a safety-net checker for pending orders.
- `StripePaymentService` is a placeholder for Stripe channel-specific adapter logic.

## Core Flow Description
1. Create payment order
- API call enters `POST /api/payment/create`.
- `OrderService.orderProcess` acquires a short Redis lock by `userId:productId`.
- Service reuses an existing `PENDING` order if present; otherwise creates a new `CREATED` order.

2. Initiate payment
- `OrderService.createStripeSession` creates Stripe Checkout Session with metadata (`orderNo`, `userId`, `product`).
- On success, order is updated to `PENDING` with `stripeSessionId` and `paymentUrl`.
- Payment ledger is moved to `PAYING` asynchronously.
- On Stripe session creation failure, order/payment are marked failed.

3. Webhook callback
- `WebhookController` receives `/api/webhook/orderAcceptor`.
- `WebhookService.handleStripeEvent` filters event types and only processes:
  - `checkout.session.completed`
  - `checkout.session.async_payment_failed`

4. Status update and idempotency
- `WebhookService.handleStripeEventProcess` first reserves `stripe_event` as `PROCESSING`.
- Duplicate `eventId` is handled by existing `stripe_event` status:
  - `SUCCEEDED` => idempotent return
  - `DEAD` or retry-exhausted => terminal handling
  - fresh `PROCESSING` => retry later
  - stale `PROCESSING` / `FAILED` => takeover and continue
- For success callback, `handlePaymentSessionSuccess` applies:
  - Redis event key (`stripe:event:{eventId}`)
  - Redisson lock (`session:lock:{sessionId}`)
  - conditional DB updates (`markPaidIfNotPaid`) for order/payment

5. Exception and failure handling
- Terminal business issues (`ORDER_NOT_FOUND`, `PAYMENT_NOT_FOUND`, `RETRY_EXHAUSTED`, etc.) are marked as terminal and returned as HTTP 200.
- Recoverable issues return non-2xx to let Stripe retry.
- Exhausted or terminal cases are upserted into `abnormalOrders` for later reconciliation.

6. Reconciliation
- `AbnormalOrderReconcileJob` runs fixed-delay and calls `AbnormalOrderService.retryAbnormalOrder`.
- Reconcile logic retrieves Stripe Session and applies status-specific repair:
  - `ORDER_MISSING` => create paid order
  - `PAYMENT_MISSING` => create success payment
  - `RETRY_EXHAUSTED` => patch existing order/payment to final success when possible

## Key Design Points
- Idempotency is layered:
  - event-level via `stripeEvents.eventId`
  - cache-level via Redis event key
  - data-level via conditional update SQL (`markPaidIfNotPaid`)
- Concurrency control is layered:
  - Redisson lock by `sessionId`
  - DB pessimistic lock for `stripe_event` row
- Error strategy is explicit:
  - recoverable => keep retry path open
  - terminal/exhausted => move to abnormal pipeline
- Failure trace durability:
  - abnormal upsert uses independent transaction (`REQUIRES_NEW`)
  - event failure/dead updates use upsert path for visibility race tolerance
- Reconcile path is bounded batch processing with retry scheduling fields (`retryCount`, `nextRetryAt`).

## Mermaid Flowchart
```mermaid
flowchart TD
    A[Client: POST /api/payment/create] --> B[OrderService.orderProcess]
    B --> C{Redis submit lock acquired?}
    C -- No --> C1[Reject duplicate submit]
    C -- Yes --> D[createOrder]
    D --> E{Existing PENDING order?}
    E -- Yes --> E1[Return existing paymentUrl]
    E -- No --> F[Create Stripe Checkout Session]

    F --> G{Session created?}
    G -- No --> G1[Order FAILED + Payment FAILED]
    G -- Yes --> H[Order -> PENDING + sessionId + paymentUrl]
    H --> I[Payment -> PAYING]

    J[Stripe webhook callback] --> K[WebhookService.handleStripeEvent]
    K --> L{Type filter}
    L -- completed/async_failed --> M[handleStripeEventProcess]
    L -- others --> L1[Ignore]

    M --> N[Reserve stripe_event as PROCESSING]
    N --> O{Duplicate eventId?}
    O -- No --> P[Process business handler]
    O -- Yes --> O1[Load existing stripe_event]

    O1 --> O2{status SUCCEEDED?}
    O2 -- Yes --> O3[Idempotent return]
    O2 -- No --> O4{status DEAD or attempts exhausted?}
    O4 -- Yes --> O5[Mark terminal + upsert abnormal]
    O4 -- No --> O6{status PROCESSING and lease fresh?}
    O6 -- Yes --> O7[Return retryable error]
    O6 -- No --> O8[Takeover -> PROCESSING]

    P --> Q{Business success?}
    Q -- Yes --> Q1[stripe_event -> SUCCEEDED]
    Q -- No --> R{Terminal business error?}
    R -- Yes --> R1[stripe_event -> DEAD]
    R -- No --> R2[stripe_event -> FAILED]

    Q1 --> S[WebhookController maps HTTP]
    R1 --> S
    R2 --> S
    O7 --> S

    S --> T{Terminal category?}
    T -- Yes --> T1[Return 200]
    T -- No --> T2[Return non-2xx for Stripe retry]

    U[Scheduler] --> V[AbnormalOrderService.retryAbnormalOrder]
    V --> W[Load retry candidates]
    W --> X[Lock by sessionId + retrieve Stripe Session]
    X --> Y{Stripe paid + complete?}
    Y -- No --> Y1[Mark UNPAID_CONFIRMED]
    Y -- Yes --> Z[checkAndFixOrderAndPayment]

    Z --> ZA{ORDER_MISSING}
    ZA -- Yes --> ZA1[Create paid order]
    Z --> ZB{PAYMENT_MISSING}
    ZB -- Yes --> ZB1[Create success payment]
    Z --> ZC{RETRY_EXHAUSTED}
    ZC -- Yes --> ZC1[Patch order/payment]

    ZA1 --> ZD[Mark FIXED]
    ZB1 --> ZD
    ZC1 --> ZD
```
