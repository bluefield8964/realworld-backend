# Payment Module TODO

## Goal
Strengthen the current Stripe-based payment module for maintainability, resilience, observability, and future multi-provider support.

## Priority Order
1. PaymentChannel abstraction
2. Webhook event policy/classifier
3. Reconcile command service
4. Metrics + alerting
5. Immutable audit timeline
6. Retry/backoff policy component
7. Signature + payload guard component
8. Manual review workflow component
9. Idempotency key manager
10. Ops dashboard query layer

---

## 1) PaymentChannel Abstraction
### Why this exists
Decouple core payment flow from Stripe-specific SDK code so future providers do not force rewrites.

### What to implement
- `PaymentChannel` interface with methods like:
  - `createCheckoutSession(...)`
  - `retrieveSession(...)`
  - `parseWebhookEvent(...)`
- `StripePaymentService` implements `PaymentChannel`.

### What to concentrate on
- Keep `OrderService` and `WebhookService` dependent on interface, not Stripe classes.
- Keep mapping from provider payload -> internal DTO in one place.
- Ensure metadata contract (`orderNo`, `userId`, `product`) is enforced centrally.

### Done criteria
- No direct Stripe SDK calls in orchestration layers except adapter layer.

---

## 2) Webhook Event Policy / Classifier
### Why this exists
Your current terminal vs recoverable logic is spread across conditionals; policy drift causes inconsistent status/HTTP behavior.

### What to implement
- `WebhookErrorPolicy` with methods like:
  - `isTerminal(ErrorCode)`
  - `httpStatusFor(ErrorCode)`
  - `stripeEventStatusFor(ErrorCode)`

### What to concentrate on
- Keep one single source for:
  - `FAILED` vs `DEAD`
  - `200` vs `non-2xx`
- Ensure controller mapping and `stripe_event` status mapping are always aligned.

### Done criteria
- No duplicated terminal-list condition chains in multiple classes.

---

## 3) Reconcile Command Service
### Why this exists
`AbnormalOrderService` currently carries many responsibilities and branching rules in one place.

### What to implement
- `ReconcileService` with explicit commands:
  - `fixOrderMissing(session)`
  - `fixPaymentMissing(session)`
  - `fixRetryExhausted(session)`

### What to concentrate on
- Keep each repair path idempotent and independently testable.
- Return structured result: `FIXED`, `RETRY_LATER`, `MANUAL_REVIEW`.

### Done criteria
- Each abnormal status has one dedicated repair method and unit tests.

---

## 4) Metrics + Alerting
### Why this exists
Without metrics, you cannot see retry storms, lock contention, or dead-event growth early.

### What to implement
- Counters:
  - webhook received/succeeded/failed/dead
  - abnormal created/fixed/manual
  - lock acquisition failure count
- Timers:
  - webhook processing latency
  - reconcile latency
- Alerts:
  - dead events above threshold
  - manual review queue growth

### What to concentrate on
- Metrics labels should be stable and low-cardinality.
- Alert thresholds should avoid noisy false positives.

### Done criteria
- You can answer "what is failing now" in < 1 minute.

---

## 5) Immutable Payment Audit Timeline
### Why this exists
Current mutable state tables are good for current status, but weak for historical investigation.

### What to implement
- `payment_audit_log` append-only table:
  - `eventId`, `sessionId`, `oldStatus`, `newStatus`, `reason`, `createdAt`

### What to concentrate on
- Never update audit rows; only insert.
- Include enough context to reconstruct incidents quickly.

### Done criteria
- Every key state transition emits one audit row.

---

## 6) Retry / Backoff Policy Component
### Why this exists
Retry timing logic is currently implicit in multiple places.

### What to implement
- `RetryPolicy` component:
  - `nextRetryAt(attempt, base, max)`
  - optional jitter
  - max-attempt decision

### What to concentrate on
- Keep webhook retries and abnormal-order retries conceptually separate.
- Avoid synchronized spikes (thundering herd) with jitter if traffic increases.

### Done criteria
- Retry math lives in one place and is reused by all retry branches.

---

## 7) Signature + Payload Guard Component
### Why this exists
Webhook safety checks should be centralized and strict.

### What to implement
- `WebhookValidator`:
  - signature verify
  - required field guards
  - event type allowlist

### What to concentrate on
- Fail fast before expensive logic.
- Distinguish malformed payload vs recoverable processing error.

### Done criteria
- All webhook preconditions validated in one component.

---

## 8) Manual Review Workflow Component
### Why this exists
`MANUAL_REVIEW` status should not be a dead-end status without process ownership.

### What to implement
- Fields/process:
  - assignee
  - reviewedAt
  - resolution note
  - resolved status
- Optional admin endpoint for case lifecycle.

### What to concentrate on
- Track owner and SLA for each manual case.
- Keep actions auditable.

### Done criteria
- Every manual-review case can be assigned, progressed, and closed.

---

## 9) Idempotency Key Manager
### Why this exists
Redis key conventions/TTL decisions are currently scattered.

### What to implement
- `IdempotencyService`:
  - build key
  - read/write with consistent TTL
  - utility for event/session scopes

### What to concentrate on
- Keep key naming consistent and documented.
- Decide clearly when to write key in terminal scenarios.

### Done criteria
- Idempotency key operations no longer duplicated across services.

---

## 10) Ops Dashboard Query Layer
### Why this exists
Troubleshooting needs fast read views without digging through multiple tables manually.

### What to implement
- Query service/read model for:
  - recent dead events
  - abnormal queue by status
  - top failure reasons
  - reconcile success rate

### What to concentrate on
- Keep queries efficient and index-friendly.
- Prefer read-only DTO projection over entity-heavy reads.

### Done criteria
- On-call can inspect payment health from one screen/API.

---

## Cross-Cutting Focus Areas (Most Important)

### A) Status Semantics Consistency
- `stripe_event` status, HTTP response to Stripe, and abnormal-order handoff must always agree.
- Never let "DB says FAILED" while controller returns terminal 200 unless explicitly intended.

### B) Idempotency + Concurrency Layering
- Event-level idempotency (eventId)
- Session-level lock (sessionId)
- Data-level idempotent update SQL
- Keep all three; they solve different race classes.

### C) Transaction Boundary Clarity
- Main transaction: payment truth updates.
- Independent transaction (`REQUIRES_NEW`): failure traces/abnormal records.
- Avoid mixed semantics where failure logs can disappear with rollback.

### D) Terminal vs Recoverable Decision Discipline
- Terminal: hand off to abnormal and stop Stripe retry.
- Recoverable: non-2xx and allow Stripe retry.
- Keep this rule explicit in policy component.

### E) Test Coverage You Should Prioritize
- Duplicate webhook eventId
- Concurrent same sessionId callbacks
- `ORDER_MISSING` and `PAYMENT_MISSING`
- Max-attempt to `DEAD`
- Reconcile fixing each abnormal status path

---

## Suggested Milestone Plan
- M1: `WebhookErrorPolicy` + `PaymentChannel` interface + minimal tests
- M2: `ReconcileService` extraction + retry policy centralization
- M3: metrics/alerts + audit timeline
- M4: manual-review workflow + ops dashboard
