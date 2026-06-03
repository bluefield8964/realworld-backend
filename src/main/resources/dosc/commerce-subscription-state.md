# Commerce Subscription State

## Purpose
This file explains how subscription state moves through the system and how the local state machine is used.

It focuses on one thing:
**how the subscription gets from checkout to provider truth to entitlement-ready state.**

## Subscription state is not one state machine
The implementation is easier to understand if you split it into phases:

1. creation phase
2. checkout phase
3. lifecycle phase
4. invoice fact phase
5. snapshot sync phase
6. reconcile phase

Each phase has a different responsibility.

## 1. Creation phase
This phase starts when the user asks to create a subscription checkout.

### Local flow
`SubscriptionService.createOrReuseSubscriptionCheckout(...)`:
- acquires a short Redis lock
- creates or reuses a local `CustomerSubscription`
- resolves the subscription plan
- writes an initial subscription row
- creates a Stripe checkout session
- persists `PENDING` when checkout session exists
- persists `INITIAL_FAIL` when session creation fails early

### Meaning
- `CREATED`
  - local row exists but checkout has not yet been established
- `INITIAL_FAIL`
  - local checkout creation failed before a usable checkout session was created
  - terminal for that creation attempt
- `PENDING`
  - checkout session exists locally
  - the customer still needs to finish the provider checkout

## 2. Checkout phase
This phase handles checkout-session events.

### Events
- `checkout.session.completed`
- `checkout.session.async_payment_failed`
- `checkout.session.expired`

### Legal transitions
- `PENDING -> PAYING`
- `PENDING -> CHECKOUT_FAIL`
- `PENDING -> CHECKOUT_EXPIRED`
- `CHECKOUT_FAIL -> PAYING`
- `CHECKOUT_FAIL -> CHECKOUT_EXPIRED`
- `PAYING -> CHECKOUT_FAIL`

### Meaning
- `PAYING`
  - checkout has completed
  - provider settlement is still pending
- `CHECKOUT_FAIL`
  - async confirmation failed, but the window is not necessarily over
- `CHECKOUT_EXPIRED`
  - the checkout attempt is over

### Boundary rule
Checkout events do not grant access.
They only move the subscription through the initialization phase.

## 3. Lifecycle phase
This is the true subscription-status layer.

### Events
- `customer.subscription.created`
- `customer.subscription.updated`
- `customer.subscription.deleted`
- `customer.subscription.paused`
- `customer.subscription.resumed`

### Important rule
`SUBSCRIPTION_CREATED` and `SUBSCRIPTION_UPDATED` are evaluated against provider status first.

That means:
1. extract provider subscription status
2. map it to local `targetStatus`
3. evaluate whether the transition is legal

### Allowed transitions
- `PENDING / PAYING / CHECKOUT_FAIL -> ACTIVE / TRIALING / PAST_DUE / PAUSED / CANCELED`
- `TRIALING -> ACTIVE / PAST_DUE / PAUSED / CANCELED`
- `ACTIVE -> PAST_DUE / PAUSED / CANCELED`
- `PAST_DUE -> ACTIVE / PAUSED / CANCELED`
- `PAUSED -> ACTIVE / CANCELED`

### Illegal back-jumps
These are rejected:
- `ACTIVE -> TRIALING`
- `PAUSED -> TRIALING`
- `PAST_DUE -> TRIALING`

### Meaning of lifecycle statuses
- `ACTIVE`
  - provider says the subscription is active
- `TRIALING`
  - provider says the subscription is in trial
- `PAST_DUE`
  - provider says payment is overdue
- `PAUSED`
  - provider says the subscription is paused
- `CANCELED`
  - provider says the subscription is canceled

### Late events
Late checkout events or late lifecycle events are ignored if a stronger later state already exists.
This is how resurrection is blocked.

## 4. Invoice fact phase
Invoice is fact storage, not the main subscription state owner.

### Events
- `invoice.payment_succeeded`
- `invoice.payment_failed`
- `invoice.payment_action_required`

### What invoice does
- stores billing facts
- refreshes snapshot fields
- helps entitlement decide whether the current cycle is grantable

### What invoice does not do
- it does not directly own the subscription lifecycle state
- it does not overwrite lifecycle truth

## 5. Snapshot sync phase
This phase keeps provider fields aligned without changing the lifecycle rule set.

### Typical fields updated here
- provider subscription id
- provider customer id
- current period start
- current period end
- cancel at period end
- canceled at timestamp

### Typical snapshot events
- `customer.subscription.updated`
- `customer.subscription.trial_will_end`

Snapshot sync is intentionally weaker than lifecycle truth.

## 6. Subscription history phase
`SubscriptionHistoryService` records the decision trail.

### Why history matters
It gives you an audit trail separate from the current subscription row.

### What it records
- init
- fail
- pending
- checkout fail
- checkout expired
- active
- canceled
- paying

This makes it possible to understand what happened even after the live row changed many times.

## 7. Reconcile phase
`SubscriptionStalledReconcileService` is the repair loop for stuck subscriptions.

### Stale `PENDING`
If a subscription stays in `PENDING` too long:
- the system logs an incident
- it does not blindly repair, because the checkout-side provider session id may not be reliable enough for active recovery in every case

### Stale `PAYING`
If a subscription stays in `PAYING` too long:
- the system retrieves provider subscription truth
- maps provider status to local status
- applies the state machine
- updates the local row with compare-and-set semantics
- refreshes entitlement

### Why reconcile exists
Webhooks alone are not enough.
The background repair loop is required for:
- missing callbacks
- provider retries
- stale local state
- out-of-order event delivery

## 8. State nature
The state machine also classifies states by nature:
- `CREATED`, `PENDING`, `PAYING`
  - intermediate
- `CHECKOUT_FAIL`
  - recoverable
- `ACTIVE`, `TRIALING`, `PAST_DUE`, `PAUSED`
  - recoverable
- `INITIAL_FAIL`, `CHECKOUT_EXPIRED`, `CANCELED`
  - terminal

This classification is used when the orchestration layer decides whether an event can still be recovered or should be treated as final.

## Short summary
Subscription flow is: create checkout row, move through checkout, sync provider lifecycle, store invoice facts, project entitlement, and repair stalled cases in reconcile.
