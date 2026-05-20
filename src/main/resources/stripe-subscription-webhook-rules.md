# Stripe Subscription Webhook Rules

## 1. Purpose
This document defines the subscription webhook rules for the current backend.

It has two goals:
- describe what the code already supports now
- describe how to extend the chain later without breaking idempotency or identity mapping

## 2. Current Runtime Reality

### Supported event families now
- `checkout.session.completed`
- `checkout.session.async_payment_failed`
- `customer.subscription.created`
- `customer.subscription.deleted`
- `invoice.payment_succeeded`
- `invoice.payment_failed`
- `invoice.paid`

### Not yet supported as active business handlers
- `customer.subscription.updated`
- `customer.subscription.trial_will_end`
- `customer.subscription.paused`
- `customer.subscription.resumed`
- `invoice.created`
- `invoice.finalized`
- `invoice.upcoming`
- `invoice.payment_action_required`

Unsupported events are currently accepted and ignored by the webhook ingress path.

## 3. Identity Model

### Local business key
- `subscriptionNo`

### Provider keys
- `providerSubscriptionId`
  - Stripe subscription id, for example `sub_xxx`
  - this must be unique in local storage
- `providerCustomerId`
  - Stripe customer id, for example `cus_xxx`
  - auxiliary lookup key, not the first subscription repair key
- `invoiceId`
  - Stripe invoice id, for example `in_xxx`

### Current storage location
The current project stores provider-side subscription identity directly on `customer_subscriptions`:
- `provider_subscription_id`
- `provider_customer_id`

This is enough for the current phase. A separate mapping table is not required yet.

## 4. Resolution Priority

### For `checkout.session.*`
Use:
1. `metadata.subscriptionNo`
2. `client_reference_id`
3. provider identifiers from the session itself for backfill

### For `customer.subscription.*`
Use:
1. `metadata.subscriptionNo`
2. `subscriptionObject.id` -> lookup local `providerSubscriptionId`

Important:
- `customer.subscription.*` objects usually do not have `sessionId`
- `subscriptionObject.id` is the correct durable provider-side unique key

### For `invoice.*`
Use:
1. `metadata.subscriptionNo`
2. `invoice.subscription` -> lookup local `providerSubscriptionId`
3. `invoice.customer` only as a weaker auxiliary fallback when necessary

## 5. Current Persistence Rule

The system must backfill provider identity as early as possible.

### Backfill sources now
- `checkout.session.*` in subscription mode
  - read `session.subscription`
  - read `session.customer`
  - write them into local `providerSubscriptionId` and `providerCustomerId`
- `customer.subscription.*`
  - read `subscriptionObject.id`
  - read `subscriptionObject.customer`
  - backfill local provider ids again if needed
- `invoice.*`
  - write invoice-side provider ids into invoice records

### Why
Once `providerSubscriptionId` is stored locally, later subscription webhook retries can still map the business subscription even if metadata is missing.

## 6. Current Handler Behavior

### `checkout.session.completed` in subscription mode
- maps by `metadata.subscriptionNo`
- moves local subscription from `CREATED` to `PENDING`
- backfills `providerSubscriptionId` and `providerCustomerId` when present

### `customer.subscription.created`
- first tries `metadata.subscriptionNo`
- if metadata is missing, falls back to `subscriptionObject.id` via local unique `providerSubscriptionId`
- moves local subscription from `PENDING` or `CREATED` to `ACTIVE`
- backfills provider ids

### `customer.subscription.deleted`
- first tries `metadata.subscriptionNo`
- if metadata is missing, falls back to `subscriptionObject.id` via local unique `providerSubscriptionId`
- marks local subscription canceled
- backfills provider ids and canceled timestamp

### `invoice.payment_succeeded`
- first tries `metadata.subscriptionNo`
- if missing after retries, falls back through provider ids
- upserts invoice accounting record
- restores local subscription to `ACTIVE`

### `invoice.payment_failed`
- first tries `metadata.subscriptionNo`
- after retry threshold, falls back through provider ids
- upserts failed invoice accounting record
- moves local subscription to canceled path according to current business rule

## 7. Idempotency Rules

The subscription chain is not allowed to rely on only one lock.

### Current layers
- webhook event reservation in `businessEvents`
- Redis idempotent key per webhook event
- Redisson lock per business tracking id
- conditional database updates on business state

### Expected semantics
- already succeeded event -> idempotent return
- fresh processing by another worker -> retry later
- stale processing or failed event -> takeover
- retry exhausted -> abnormal pipeline

## 8. When Metadata Is Missing

### Correct handling
- do not immediately assume the event is unusable
- try `providerSubscriptionId` lookup first for `customer.subscription.*`
- only return retryable failure when both metadata and local provider mapping are unavailable

### Why not rely only on metadata
- Stripe object families carry different fields
- `sessionId` disappears outside checkout-session events
- future renewals are driven by `subscriptionId` and `invoiceId`, not by the original checkout session

## 9. Recommended Future Extensions

### Phase 1: complete the subscription lifecycle
Add:
- `customer.subscription.updated`
- `customer.subscription.trial_will_end`
- `customer.subscription.paused`
- `customer.subscription.resumed`

Recommended behavior:
- `updated`
  - sync `status`
  - sync `cancel_at_period_end`
  - sync period start / end
  - sync pause and pending-update information if needed
- `trial_will_end`
  - usually notify or mark upcoming transition, not a terminal business transition
- `paused` / `resumed`
  - narrow state transitions only

### Phase 2: action-required and billing detail
Add:
- `invoice.payment_action_required`
- `invoice.finalized`
- `invoice.created`

Recommended behavior:
- `payment_action_required`
  - create an actionable local state for SCA / payment method confirmation
- `invoice.finalized`
  - accounting visibility
- `invoice.created`
  - pre-billing trace only, not final business success

### Phase 3: projection split if needed
If later you need more Stripe-specific keys than the current entity should hold, introduce a dedicated mapping projection such as:

`subscription_identifiers`
- `subscriptionNo`
- `provider`
- `providerSubscriptionId`
- `providerCustomerId`
- `latestInvoiceId`
- `latestPaymentIntentId`
- `lastWebhookEventId`
- `updatedAt`

Do this only when current entity fields become insufficient. Right now the simpler design is still the right one.

## 10. Coding Rule For Future Handlers

When adding a new subscription webhook handler, follow this order:
1. parse the correct Stripe object type
2. resolve local business identity
3. backfill provider ids if missing
4. apply narrow business state transition
5. mark event success or failure through the orchestrator pipeline
6. send terminal vs retryable HTTP semantics through the shared error policy

## 11. Short Conclusion

For this project, the durable truth for subscription repair is:
- business side uses `subscriptionNo`
- Stripe side uses unique `providerSubscriptionId`
- metadata is preferred when present
- `providerSubscriptionId` is the required fallback for `customer.subscription.*`
