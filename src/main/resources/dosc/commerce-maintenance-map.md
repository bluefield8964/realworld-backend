# Commerce Maintenance Map

## Goal
This file is a maintenance guide, not a business-spec document.
Use it to answer two questions quickly:
- which class should change for this behavior?
- which layer owns this responsibility?

## Read this first
If you want the full explanation, read:
- `commerce.md`
- `commerce-truth-boundary.md`
- `commerce-subscription-state.md`
- `commerce-webhook-architecture.md`
- `commerce-entitlement.md`
- `commerce-abnormal-and-incident.md`

## Quick route map

### Checkout creation
- `PaymentController`
  - API entry for order checkout and subscription checkout
- `OrderService`
  - one-time order checkout creation and reuse
- `SubscriptionService`
  - subscription checkout creation and reuse
- `StripePaymentChannel`
  - provider-specific checkout session creation

### Webhook orchestration
- `WebhookController`
  - external Stripe webhook entry
- `WebhookOrchestrator`
  - parse -> reserve -> route -> execute -> finalize
- `EventReservationService`
  - event ownership, takeover, and idempotency
- `PaymentFailureEscalationService`
  - decide incident vs abnormal vs retry
- `WebhookIncidentOrchestrator`
  - write and merge incidents

### Subscription lifecycle
- `SubscriptionWebhookStateMachine`
  - legal transition rules only
- `SubscriptionService`
  - creation phase and checkout state bootstrap
- `SubscriptionStalledReconcileService`
  - repair stale `PENDING` / `PAYING` rows
- `SubscriptionHistoryService`
  - append audit history

### Entitlement
- `EntitlementProjector`
  - convert subscription truth into access projection
- `EntitlementStatusMachine`
  - decide grant / revoke / expire
- `EntitlementFacade`
  - query facade for controllers and services

### Abnormal repair
- `OrderAbnormalService`
  - repair one-time order anomalies
- `SubscriptionAbnormalService`
  - repair subscription and invoice anomalies
- `AbnormalOrchestrator`
  - write abnormal records and dispatch follow-up work

## If you need to change X

- create a new one-time checkout:
  - start at `OrderService`
- create a new subscription checkout:
  - start at `SubscriptionService`
- change provider checkout session details:
  - start at `StripePaymentChannel`
- change webhook retry vs terminal behavior:
  - start at `WebhookErrorPolicy`
- change webhook incident persistence:
  - start at `WebhookIncidentOrchestrator`
- change abnormal classification:
  - start at `PaymentFailureEscalationService`
- change subscription state movement:
  - start at `SubscriptionWebhookStateMachine`
- change entitlement rules:
  - start at `EntitlementStatusMachine`
- change entitlement read APIs:
  - start at `EntitlementQueryService`

## Naming conventions
- `createOrReuse*Checkout`
  - API-triggered checkout creation entrypoints
- `handle*Event`
  - direct webhook event handlers
- `activate*`, `mark*`, `upsert*`, `backfill*`
  - explicit state-transition or persistence helper methods
- `providerSubscriptionId`
  - durable Stripe subscription identity
- `providerCustomerId`
  - Stripe customer identity
- `subscriptionNo`
  - local business key for subscriptions

## Future expansion guidance
- When Stripe adds a new event you want to support, expand in this order:
  1. normalize provider event name into `BusinessEventType`
  2. add or extend the dedicated handler
  3. keep handler thin and put state transitions into the relevant domain service
  4. decide whether the failure is retryable, terminal, or repairable
- Avoid putting provider-specific JSON traversal directly into orchestrators.
- Avoid mixing order semantics into subscription services or the reverse.
- If subscription identity mapping becomes more complex later, extract a dedicated identity projection instead of overloading webhook handlers.
