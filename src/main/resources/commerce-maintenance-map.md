# Commerce Maintenance Map

## Goal
- This file is a maintenance guide, not a business-spec document.
- Use it to answer two questions quickly:
  - "which class should I modify for this behavior?"
  - "which layer owns this responsibility?"

## Entry Layer
- `PaymentController`
  - external API entry for checkout creation
  - order endpoint: `POST /api/payment/createOrder`
  - subscription endpoint: `POST /api/payment/createSubscription`
- `WebhookController`
  - external Stripe webhook entry
  - owns HTTP response semantics for retryable vs terminal webhook failures

## Checkout Creation Layer
- `OrderService`
  - one-time order checkout creation and pending-order reuse
  - owns `Order` creation, checkout session creation, and local order persistence
- `SubscriptionService`
  - subscription checkout creation and pending-subscription reuse
  - owns `CustomerSubscription` creation, checkout session creation, and initial subscription persistence

## Webhook Orchestration Layer
- `WebhookOrchestrator`
  - top-level webhook pipeline coordinator
  - sequence: parse -> normalize -> reserve -> dispatch -> finalize
- `EventReservationService`
  - event idempotency, takeover, and event-row state transitions
- `BusinessEventService`
  - persistence helper for `businessEvents`

## Provider Adapter Layer
- `PaymentChannel`
  - provider abstraction for checkout creation and webhook parsing
- `PaymentChannelRouter`
  - picks the concrete provider adapter
- `StripePaymentChannel`
  - current Stripe implementation
- `ProviderRawEvent`
  - provider-neutral raw event container after signature verification and parsing
- `EventAnticorruptionLayer`
  - converts provider event names into internal `BusinessEventType`

## Webhook Business Handler Layer
- `CheckoutSessionWebhookService`
  - handles `checkout.session.*`
  - splits by Stripe Checkout mode:
    - `payment` -> one-time order path
    - `subscription` -> subscription checkout path
- `SubscriptionWebhookService`
  - handles `customer.subscription.*` and `invoice.*`
  - owns subscription activation, cancellation, renewal success, and renewal failure transitions
- `WebhookHandlerRouter`
  - maps normalized `BusinessEventType` to the right handler
- `service.impl.handler.*`
  - thin event-specific handlers
  - use these when adding a new routed Stripe event

## Abnormal and Reconcile Layer
- `AbnormalOrchestrator`
  - writes abnormal records and dispatches reconcile work by domain
- `OrderAbnormalService`
  - reconciles one-time order anomalies
- `SubscriptionAbnormalService`
  - reconciles subscription and invoice anomalies
- `AbnormalOrderReconcileJob`
  - scheduled trigger for abnormal retry polling

## Model Grouping
- `model`
  - order/payment/abnormal core entities for one-time commerce flow
- `model.core`
  - provider-neutral transport and command objects
- `model.checkoutPayment`
  - checkout-session webhook payload shape
- `model.invoice`
  - invoice webhook payload and invoice entity
- `model.subscription`
  - subscription aggregate, plan mapping, history, and webhook payload shape
- `model.subscription.enums`
  - subscription-only enums

## If You Need To Change X
- create a new one-time payment checkout:
  - start at `OrderService`
- create a new subscription checkout:
  - start at `SubscriptionService`
- change how Stripe payloads are verified or parsed:
  - start at `StripePaymentChannel`
- add a new normalized event type:
  - update `BusinessEventType`
  - update `EventAnticorruptionLayer`
  - add a handler under `service.impl.handler`
  - route business logic into the correct webhook service
- change checkout-session payment success semantics:
  - start at `CheckoutSessionWebhookService.handleOrderPaymentCompleted`
  - final state transition lives in `handlePaymentSessionSuccess`
- change subscription creation/cancel webhook semantics:
  - start at `SubscriptionWebhookService.handleSubscriptionCreatedEvent`
  - start at `SubscriptionWebhookService.handleSubscriptionDeletedEvent`
- change invoice renewal success/failure semantics:
  - start at `SubscriptionWebhookService.handleInvoicePaymentSucceeded`
  - start at `SubscriptionWebhookService.handleInvoicePaymentFailed`
- change retryable vs terminal webhook behavior:
  - start at `WebhookErrorPolicy` and `DefaultWebhookErrorPolicy`
- change abnormal retry classification:
  - start at `AbnormalOrchestrator`

## Naming Conventions In This Module
- `createOrReuse*Checkout`
  - API-triggered checkout creation entrypoints
- `handle*Event`
  - direct webhook event handlers
- `activate*`, `mark*`, `upsert*`, `backfill*`
  - explicit state-transition or persistence helper methods
- `providerSubscriptionId`
  - durable Stripe `sub_xxx` identity
- `providerCustomerId`
  - Stripe `cus_xxx` identity
- `subscriptionNo`
  - local business primary key for subscriptions
- `abnormalOrders.sessionId`
  - shared provider-object tracking column
  - order domain stores checkout session id `cs_xxx`
  - invoice domain stores invoice id `in_xxx`
  - subscription domain may store provider subscription id `sub_xxx` as fallback

## Future Expansion Guidance
- When Stripe adds a new event you want to support, expand in this order:
  1. normalize provider event name into `BusinessEventType`
  2. add a dedicated handler class
  3. keep handler thin and put state transitions into the relevant domain service
  4. decide whether failure is retryable, terminal, or abnormal-reconcile
- Avoid putting provider-specific JSON traversal directly into orchestrators.
- Avoid mixing order semantics into subscription services or the reverse.
- If subscription identity mapping becomes more complex later, extract a dedicated identity projection instead of overloading webhook handlers.
