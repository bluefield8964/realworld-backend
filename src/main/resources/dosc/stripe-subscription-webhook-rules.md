# Stripe Subscription Webhook Rules

This file is now a short reference.
For the full live behavior, read:
- `commerce-webhook-architecture.md`
- `commerce-subscription-state.md`
- `commerce-truth-boundary.md`

## Current runtime facts

### Supported active families
- `checkout.session.completed`
- `checkout.session.async_payment_failed`
- `checkout.session.expired`
- `customer.subscription.created`
- `customer.subscription.updated`
- `customer.subscription.deleted`
- `customer.subscription.paused`
- `customer.subscription.resumed`
- `invoice.payment_succeeded`
- `invoice.payment_failed`
- `invoice.payment_action_required`

### Core truth rule
- checkout events move the checkout phase
- subscription events move lifecycle truth
- invoice events store billing facts
- entitlement is derived from subscription + invoice

### Identity rule
- `subscriptionNo` is the local business key
- `providerSubscriptionId` is the durable Stripe-side repair key
- metadata is preferred when present, but provider ids are the required fallback for later repair

### Extension rule
When adding a new handler:
1. parse the provider object
2. resolve local identity
3. backfill provider ids
4. apply the narrowest legal transition
5. let the orchestrator decide retry vs terminal behavior

