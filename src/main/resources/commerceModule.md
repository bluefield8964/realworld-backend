# Commerce Module

## Purpose
- This module owns two payment domains:
  - one-time order checkout
  - recurring subscription checkout and subscription webhook maintenance
- The runtime is Stripe-only today, but the code is already split into:
  - provider adapter
  - event normalization
  - event reservation / idempotency
  - business handlers
  - abnormal reconciliation
  - incident recording

## Real Runtime Entry Points
- `POST /api/payment/createOrder`
  - implemented by `PaymentController -> OrderService.createOrReuseOrderCheckout(...)`
- `POST /api/payment/createSubscription`
  - implemented by `PaymentController -> SubscriptionService.createOrReuseSubscriptionCheckout(...)`
- `POST /api/webhook/stripeOrderAcceptor`
  - implemented by `WebhookController -> WebhookOrchestrator.process(...)`

## High-Level Architecture

### API creation side
- `PaymentController`
  - thin HTTP entrypoint for order checkout and subscription checkout
- `OrderService`
  - local order creation / reuse
  - Stripe Checkout Session creation in `payment` mode
  - local payment ledger bootstrap
- `SubscriptionService`
  - local subscription creation / reuse
  - Stripe Checkout Session creation in `subscription` mode
  - initial subscription history bootstrap

### Webhook side
- `WebhookController`
  - Stripe-facing adapter
  - converts internal classification into HTTP 200 or retryable non-2xx
- `WebhookOrchestrator`
  - provider parse and signature verification
  - event normalization
  - event reservation / takeover
  - handler routing
  - event status persistence
  - fallback abnormal / incident escalation
- `EventReservationService`
  - owns event idempotency and retry budget at `business_events`
- `WebhookHandlerRouter`
  - dispatches normalized events to concrete business handlers
- `CheckoutSessionWebhookService`
  - handles `checkout.session.*`
  - internally splits `mode=payment` and `mode=subscription`
- `SubscriptionWebhookService`
  - handles `customer.subscription.*` and `invoice.*`

### Recovery and observability side
- `BusinessEventService`
  - persists webhook event processing state
- `AbnormalOrchestrator`
  - upserts `abnormal_orders`
  - drives reconcile retries by domain
- `OrderAbnormalService`
  - repairs one-time order/payment anomalies by retrieving Stripe session
- `SubscriptionAbnormalService`
  - repairs subscription/invoice anomalies by retrieving Stripe subscription/invoice
- `WebhookIncidentOrchestrator`
  - deduplicates and aggregates system-level incidents in `webhook_incidents`
- `PaymentFailureEscalationService`
  - decides whether a failure belongs to:
    - incident only
    - abnormal only
    - both
    - neither

## Core Persistent Models

### Order-side models
- `orders`
  - local one-time order record
- `payments`
  - local payment ledger for one-time order checkout
- `payment_error_logs`
  - provider API error support trail

### Subscription-side models
- `customer_subscriptions`
  - main local subscription aggregate
- `subscription_historys`
  - append-only-ish transition record for local subscription state changes
- `subscription_invoices`
  - local accounting / invoice projection derived from Stripe invoice events
- `subscription_plans`
  - internal plan catalog
- `plan_provider_mappings`
  - maps internal plans to provider price/product ids

### Webhook control-plane models
- `business_events`
  - per-event idempotency / retry / terminal state row
- `abnormal_orders`
  - repairable or manually escalated abnormal records
- `webhook_incidents`
  - system-level incident aggregation

## Supported Runtime Webhook Events

### Actually routed to handlers
- `checkout.session.completed`
- `checkout.session.async_payment_failed`
- `customer.subscription.created`
- `customer.subscription.deleted`
- `invoice.payment_succeeded`
- `invoice.payment_failed`

### Normalized but not currently handled by a business handler
- `checkout.session.expired`
- `invoice.paid`
- `customer.subscription.trial_will_end`

### Explicitly ignored at normalization layer
- `payment_intent.payment_failed`

## Order Checkout Flow

### Main happy path
1. Client calls `POST /api/payment/createOrder`.
2. `OrderService` builds `activeKey = userId:productId`.
3. A short Redis lock `pay:lock:{activeKey}` blocks double-click submit storms.
4. `createOrder(...)` checks for an existing local `PENDING` order by `activeKey`.
5. If one exists, the old checkout URL is reused.
6. Otherwise a new local order is inserted with:
   - `status = CREATED`
   - new `orderNo`
   - `activeKey`
7. `PaymentService.recordInit(...)` writes a local payment ledger row with `INIT`.
8. `StripePaymentChannel.createCheckoutSession(...)` creates Stripe Checkout Session in `payment` mode.
9. Success path stores:
   - `orders.sessionId`
   - `orders.paymentUrl`
   - `orders.status = PENDING`
   - `payments.status = PAYING`
10. The checkout URL is returned to the caller.

### Creation-side failure path
- If Stripe session creation throws `PaymentChannelException`:
  - order is moved to `FAILED`
  - `activeKey` is cleared
  - payment ledger is marked `FAILED`
  - the exception bubbles upward

### One-time order state picture
- local order:
  - `CREATED`
  - `PENDING`
  - `PAID`
  - `FAILED`
  - `PAYMENT_FAILED_RETRYABLE`
- local payment:
  - `INIT`
  - `PAYING`
  - `SUCCESS`
  - `FAILED`

## Subscription Checkout Flow

### Main happy path
1. Client calls `POST /api/payment/createSubscription`.
2. `SubscriptionService` builds `activeKey = userId:planCode`.
3. A short Redis lock `pay:lock:{activeKey}` blocks duplicate submit storms.
4. `createSubscription(...)` checks:
   - existing `PENDING` subscription by `activeKey`
   - existing active subscription by `activeKey`
5. If a `PENDING` subscription exists, the old checkout URL is reused.
6. If an active subscription exists, the flow throws `SUBSCRIPTION_ALREADY_CREATED`.
7. Otherwise a new local subscription is inserted/upserted with:
   - new `subscriptionNo`
   - `status = CREATED`
   - `currentPeriodStart / currentPeriodEnd`
   - `activeKey`
8. `SubscriptionHistoryService.recordInit(...)` writes the initial history row.
9. `StripePaymentChannel.createSubscriptionSession(...)` creates Stripe Checkout Session in `subscription` mode.
10. The Checkout Session carries metadata on both:
   - checkout session metadata
   - subscription-data metadata
11. Key correlation fields include:
   - `subscriptionNo`
   - `userId`
   - `planCode`
   - compatibility `orderNo = subscriptionNo`
12. Session-create success moves local subscription to `PENDING`, stores the checkout URL, and appends pending history.

### Creation-side failure path
- If Stripe session creation fails:
  - history records initial failure
  - local subscription is moved to `INITIAL_FAIL`
  - exception bubbles upward

### Subscription identity model
- local business key:
  - `subscriptionNo`
- durable provider-side key:
  - `providerSubscriptionId` (`sub_xxx`)
- auxiliary provider-side key:
  - `providerCustomerId` (`cus_xxx`)

### Subscription status picture
- actively used in runtime flow:
  - `CREATED`
  - `PENDING`
  - `ACTIVE`
  - `INITIAL_FAIL`
  - `PAST_DUE`
  - `CANCELED`
- recognized mainly in reconcile / projection logic:
  - `TRIALING`
  - `PAUSED`

## Webhook Pipeline

### Stage 1: HTTP ingress
1. Stripe calls `POST /api/webhook/stripeOrderAcceptor`.
2. `WebhookController` passes:
   - raw payload
   - `Stripe-Signature`
   - configured webhook secret
   - provider=`STRIPE`

### Stage 2: provider parse and normalization
1. `WebhookOrchestrator` asks `PaymentChannelRouter` for the provider adapter.
2. `StripePaymentChannel.parseRawEvent(...)` verifies the signature and parses the Stripe event.
3. `ProviderRawEvent` is created.
4. `EventAnticorruptionLayer` maps Stripe raw event type into `BusinessEventType`.
5. If the result is `UNKNOWN_EVENT`, the webhook is accepted with HTTP 200 and stops there.

### Stage 3: event reservation and takeover
1. `EventReservationService.reserveOrTakeover(...)` tries `BusinessEventService.saveProcessing(...)`.
2. First-seen event:
   - inserts `business_events.status = PROCESSING`
   - `attempts = 0`
3. Duplicate event:
   - locks the existing row
   - branches by current status

### Stage 4: duplicate-event branch rules
- if existing status is `SUCCEEDED`
  - stop immediately as idempotent success
- if existing status is `DEAD`
  - record dead-event incident
  - optionally refresh abnormal trace
  - stop immediately
- if retry budget is exhausted
  - mark event `DEAD`
  - record retry-budget incident
  - optionally upsert abnormal
  - stop immediately
- if status is fresh `PROCESSING`
  - throw `EVENT_PROCESSING`
  - Stripe receives retryable non-2xx
- if status is stale `PROCESSING` or `FAILED`
  - take ownership again
  - move row back to `PROCESSING`
  - continue business handling

### Stage 5: business dispatch
- `WebhookHandlerRouter` dispatches by normalized `BusinessEventType`
- current handlers:
  - `CheckoutCompletedHandler`
  - `CheckoutPaymentFailedHandler`
  - `SubscriptionCreatedHandler`
  - `SubscriptionDeletedHandler`
  - `InvoicePaymentSucceededHandler`
  - `InvoicePaymentFailedHandler`

### Stage 6: success and failure persistence
- handler success:
  - `business_events` becomes `SUCCEEDED`
  - HTTP 200
- handler failure:
  - `DefaultWebhookErrorPolicy` decides:
    - `terminal or retryable`
    - `eventStatus`
    - `HTTP status`
  - `WebhookOrchestrator` persists event failure state when applicable
  - `PaymentFailureEscalationService` decides incident / abnormal side effects

## Checkout Session Webhook Split

### `checkout.session.completed`
- `CheckoutSessionWebhookService.handleCheckoutSessionCompleted(...)`
- branches by `session.mode`

#### Branch A: `mode=payment`
1. Parse `orderNo` from metadata.
2. Set context:
   - `trackingId = orderNo`
   - `providerTrackingId = sessionId`
3. Require `payment_status = paid`.
4. Enter final success routine `handlePaymentSessionSuccess(...)`.
5. Protection layers:
   - event reservation
   - Redis event idempotent key
   - Redisson lock on `sessionId`
   - SQL CAS `orders.markPaidIfNotPaid(...)`
   - SQL CAS `payments.markPaidIfNotPaid(...)`
6. Success outcome:
   - order becomes `PAID`
   - `activeKey` cleared
   - payment becomes `SUCCESS`
7. Missing order or payment:
   - upsert abnormal
   - throw `ORDER_NOT_FOUND` or `PAYMENT_NOT_FOUND`

#### Branch B: `mode=subscription`
1. Parse `subscriptionNo` from metadata.
2. Set context:
   - `trackingId = subscriptionNo`
   - `providerTrackingId = checkout session id`
3. Acquire Redisson lock on `subscriptionNo`.
4. Try CAS transition:
   - `CREATED -> PENDING`
5. Append pending history only if latest payment status still matches the expected source state.
6. Backfill `providerSubscriptionId` and `providerCustomerId` from the checkout session when available.
7. Missing local subscription or missing history:
   - upsert abnormal
   - throw retry / not-found exceptions

### `checkout.session.async_payment_failed`
- same mode split as above

#### Payment mode
- finds local order by `sessionId`
- finds local payment by `sessionId`
- if both exist and are not already finalized success:
  - order moves to `PAYMENT_FAILED_RETRYABLE`
  - `activeKey` cleared
  - payment becomes `FAILED`
- if order/payment missing:
  - upsert abnormal
  - throw terminal biz exception

#### Subscription mode
- attempts `CREATED -> INITIAL_FAIL`
- appends initial-fail history when source payment state still matches
- missing local subscription or missing history:
  - upsert abnormal
  - throw biz exception

## Subscription Webhook Flow

### `customer.subscription.created`
1. Parse provider subscription object.
2. Resolve `subscriptionNo` in this order:
   - metadata `subscriptionNo`
   - fallback local lookup by `providerSubscriptionId`
3. Set context:
   - `trackingId = subscriptionNo`
   - `providerTrackingId = providerSubscriptionId`
4. Acquire Redisson lock by `subscriptionNo`.
5. Attempt CAS:
   - `PENDING -> ACTIVE`
   - if not updated, try `CREATED -> ACTIVE`
6. Append active history if the latest payment state is acceptable.
7. Backfill provider identity fields.
8. Missing local subscription or history:
   - upsert abnormal
   - throw biz exception

### `customer.subscription.deleted`
1. Resolve `subscriptionNo` using the same metadata-first, provider-id-second strategy.
2. Acquire Redisson lock.
3. Attempt CAS:
   - `ACTIVE -> CANCELED`
4. Sync provider billing period and `cancelAtPeriodEnd`.
5. Append canceled history if the latest payment state is acceptable.
6. Backfill provider identity and `canceledAt`.
7. Missing local subscription or history:
   - upsert abnormal
   - throw biz exception

## Invoice Webhook Flow

### `invoice.payment_failed`
1. Parse invoice object.
2. Require subscription invoice semantics.
3. Resolve invoice id as provider tracking key.
4. Resolve business `subscriptionNo`:
   - first from metadata
   - if `attempts > 4`, fallback lookup by provider subscription / customer id
5. Set context:
   - `trackingId = subscriptionNo`
   - `providerTrackingId = invoiceId`
6. Acquire Redisson lock on invoice id.
7. Upsert invoice projection with `PaymentStatus.FAILED`.
8. Move local subscription only to `PAST_DUE`.
9. Missing local subscription:
   - upsert abnormal
   - throw `CUSTOMER_SUBSCRIPTION_NOT_FOUND`

### `invoice.payment_succeeded`
1. Parse invoice object.
2. Require subscription invoice semantics.
3. Resolve business `subscriptionNo`:
   - first from metadata
   - before retry threshold, missing metadata is treated as retryable parse problem
   - after retry threshold, fallback to local lookup by provider ids
4. Set context:
   - `trackingId = subscriptionNo`
   - `providerTrackingId = invoiceId`
5. Acquire Redisson lock on invoice id.
6. Upsert invoice projection with `PaymentStatus.SUCCESS`.
7. Recover local subscription only from recoverable states:
   - `PENDING`
   - `INITIAL_FAIL`
   - `PAST_DUE`
8. Success does not blindly rewrite every status to `ACTIVE`.

## Error Handling and HTTP Semantics

### The real split
- `DefaultWebhookErrorPolicy`
  - decides transport semantics
  - decides event row status
- `PaymentFailureEscalationService`
  - decides incident recording
  - decides abnormal escalation
- `AbnormalOrchestrator`
  - owns repairable abnormal queue
- `WebhookIncidentOrchestrator`
  - owns incident aggregation

### Terminal errors that return HTTP 200
- local business record already missing in a known terminal branch:
  - `ORDER_NOT_FOUND`
  - `PAYMENT_NOT_FOUND`
  - `RETRY_EXHAUSTED`
  - `ORDER_FAILED`
- non-retryable `PaymentChannelException`

### Retryable failures that return non-2xx
- payload / parse mismatch:
  - `JSON_ERROR`
  - `STRIPE_SESSION_NOT_FOUND`
  - `STATEMENT_DOES_NOT_MATCH_EVENT_TYPE`
- lock contention:
  - `LOCK_CANNOT_ACQUIRE`
  - `LOCK_INTERRUPTED`
- ownership race:
  - `EVENT_PROCESSING`
  - `EVENT_NOT_FOUND`
- retryable provider/channel exceptions

### Incident vs abnormal intent
- `abnormal_orders`
  - used for things the system believes can be repaired or at least queued for domain-specific handling
  - also used for some manual-review abnormal types
- `webhook_incidents`
  - used for system-level aggregation, dedupe, burst detection, and operator visibility

## Idempotency and Concurrency Model

### Main webhook stream
- `business_events` row per provider event id
- duplicate insert detection on first reservation
- pessimistic lock when replaying an existing event row
- stale processing lease + retry policy takeover

### Business object layer
- order success:
  - Redisson `session:lock:{sessionId}`
  - SQL `markPaidIfNotPaid`
- subscription:
  - Redisson `subscription:lock:{subscriptionNo}`
  - SQL CAS `updateFromProviderIfStatusChanged`
- invoice:
  - Redisson `invoice:lock:{invoiceId}`

### Redis idempotent keys
- order success:
  - `PaymentSessionSuccess:event:{eventId}`
- subscription handlers:
  - `handleSubscription:event:{eventId}`
- invoice handlers:
  - `handleInvoice:event:{eventId}`

These keys are used as an extra storm shield after state has been handled.

## Reconcile / Fallback Model

### Abnormal domain split
- `ORDER`
  - handled by `OrderAbnormalService`
- `SUBSCRIPTION`
  - handled by `SubscriptionAbnormalService.reconcileSubscription(...)`
- `INVOICE`
  - handled by `SubscriptionAbnormalService.reconcileInvoice(...)`
- `SYSTEM`
  - currently pushed to manual review

### Order abnormal repair
- retrieves Stripe checkout session by `sessionId`
- requires:
  - provider session is `paid`
  - provider session is `complete`
  - metadata carries `orderNo`, `userId`, `product`
  - local product price still matches provider amount
- can recreate:
  - missing `Order`
  - missing `Payment`
- otherwise moves to manual review or retry exhaustion

### Subscription abnormal repair
- retrieves Stripe subscription by `providerSubscriptionId`
- validates:
  - object type is really `subscription`
  - provider identity matches local identity
  - optional metadata `subscriptionNo` is consistent
- maps provider status into local subscription status
- backfills provider ids and billing period

### Invoice abnormal repair
- retrieves Stripe invoice by provider invoice id
- validates:
  - object type is `invoice`
  - invoice id matches expected id
  - status is one of supported accounting states
  - provider-side identity is internally consistent
- can recover local business key by:
  - tracked `subscriptionNo`
  - provider metadata `subscriptionNo`
  - local lookup from provider subscription/customer ids
- then restores local invoice projection

### Scheduler
- `AbnormalOrderReconcileJob`
  - runs every 5 minutes
  - asks `AbnormalOrchestrator.retryAbnormalOrder()` for retry candidates

## Event-Driven Semantics

### What is event-driven here
- client checkout creation is request-driven
- final commerce truth is webhook-driven
- local status transitions are intentionally delayed until Stripe webhooks confirm them

### Important domain rule
- local synchronous checkout creation does not equal payment success
- local synchronous subscription checkout creation does not equal subscription activation
- webhook events are the source of truth for final state transitions

## Current Module Problems

### 1. Native SQL table names do not match the real schema
- `BusinessEventRepository.upsertFailStatus(...)` writes to `businessEvents`
- `AbnormalOrderRepository.upsertBySessionId(...)` writes to `abnormalOrders`
- your real tables are `business_events` and `abnormal_orders`
- native SQL does not use JPA naming strategy, so this is a real runtime risk

### 2. Some normalized events are accepted but not completed
- `EventAnticorruptionLayer` maps:
  - `checkout.session.expired`
  - `invoice.paid`
  - `customer.subscription.trial_will_end`
- but there is no matching handler in `WebhookHandlerRouter`
- `WebhookOrchestrator` reserves the event before checking `handler == null`
- then it returns HTTP 200 without marking the event `SUCCEEDED`
- this can leave a `business_events` row stuck in `PROCESSING`

### 3. `EventReservationService.shouldEscalateToAbnormal(...)` is still too broad
- current condition is:
  - `(trackingId != null && providerTrackingId != null) || providerRawEvent != null`
- in normal webhook flow `providerRawEvent` is almost always present
- that means many system-level failures can still leak into `abnormal_orders`
- this partially fights the newer incident-vs-abnormal split

### 4. Support matrix is wider in enums than in real handlers
- `BusinessEventType` models many more states than the runtime actually supports
- examples:
  - `SUBSCRIPTION_PAUSED`
  - `SUBSCRIPTION_RESUMED`
  - `SUBSCRIPTION_RENEWED`
  - `PAYMENT_ACTION_REQUIRED`
- doc readers must separate:
  - modeled enum values
  - normalized-but-unhandled values
  - actually handled runtime values

### 5. Subscription history and aggregate state can diverge
- several handlers update the main subscription row first
- then append history conditionally
- when `AppendOutcome` is `LATEST_PAYMENT_STATUS_MISMATCH`, the aggregate may already move while history append is skipped
- this is not always wrong, but it means history is not a strict, guaranteed mirror of aggregate transitions

### 6. Pre-business incident and post-business abnormal ownership is conceptually split, but not fully simplified
- `PaymentFailureEscalationService` is already the intended owner for incident decisions
- `DefaultWebhookErrorPolicy` still carries `needUpsertAbnormal` in `WebhookDecision`
- `WebhookOrchestrator` mostly ignores that field and uses escalation service instead
- the design direction is good, but the API surface is still partly transitional

### 7. Provider typing is inconsistent between order and subscription entry
- order creation uses `provider` as raw `String`
- subscription creation uses `ProviderType`
- this is a maintainability issue and makes provider validation inconsistent at API boundary

### 8. Stripe redirect URLs are hardcoded in the adapter
- order flow uses `http://localhost:3000/success` / `cancel`
- subscription flow uses hardcoded `https://yoursite.com/...`
- this should eventually be externalized, otherwise environments can drift silently

### 9. Some reconcile semantics still depend on compatibility assumptions
- subscription checkout metadata keeps `orderNo = subscriptionNo` for compatibility
- invoice repair and fallback logic also reuse business order-style fields for subscription tracking
- this works now, but it is a naming compromise rather than a clean domain model

## Operational Notes and Cautions

### Do not assume all 200s mean success
- a 200 may mean:
  - business success
  - terminal local miss already handed to abnormal flow
  - unsupported event intentionally accepted

### Do not assume all non-2xx mean provider problem
- many non-2xx responses are local:
  - Redis lock contention
  - stale processing handoff
  - payload mismatch
  - event row visibility race

### Subscription lookup priority matters
- when metadata exists, use `subscriptionNo`
- when metadata is missing, use `providerSubscriptionId`
- `providerCustomerId` is secondary support data, not the first durable business key

### Invoice success/failure should be read separately from subscription activation
- invoice success recovers billing/accounting state
- `customer.subscription.created` is still the cleaner activation signal

### Local repair should always validate provider identity before writing back
- order repair validates amount, product, user, and metadata
- subscription repair validates subscription object type and provider/customer identity
- invoice repair validates invoice object type and id before restoring local records

## Recommended Reading Order In This Repo
1. `PaymentController`
2. `OrderService`
3. `SubscriptionService`
4. `WebhookController`
5. `WebhookOrchestrator`
6. `EventReservationService`
7. `CheckoutSessionWebhookService`
8. `SubscriptionWebhookService`
9. `PaymentFailureEscalationService`
10. `AbnormalOrchestrator`
11. `OrderAbnormalService`
12. `SubscriptionAbnormalService`

## Practical Summary
- one-time order success is webhook-finalized, not request-finalized
- subscription activation is webhook-finalized, not request-finalized
- `business_events` is the first idempotency wall
- Redisson locks are the second wall
- SQL CAS updates are the last wall
- `abnormal_orders` is the repair / manual-review queue
- `webhook_incidents` is the system visibility / burst / dedupe layer
- the current real runtime is coherent, but a few gaps remain between:
  - enum design
  - handler registration
  - abnormal vs incident split
  - native SQL table names
