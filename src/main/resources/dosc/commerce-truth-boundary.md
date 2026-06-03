# Commerce Truth Boundary

## Purpose
This document explains which object owns which truth.

It is the single most important conceptual map for the commerce module.

## Truth layers

### 1. Current state truth
This is the mutable business state that the system acts on now.

Owned by:
- `Order`
- `Payment`
- `CustomerSubscription`
- `Invoice`

Use it when you need to know the current state of the business object.

### 2. Audit truth
This records what happened over time.

Owned by:
- `SubscriptionHistory`

Use it when you need to answer:
- what changed
- when it changed
- why it changed

### 3. Alert truth
This records webhook problems and operational incidents.

Owned by:
- `WebhookIncident`

Use it when you need to answer:
- what failed
- whether it was retryable
- whether it should become an alert or a ticket

### 4. Repair truth
This stores recoverable business debt.

Owned by:
- `AbnormalOrder`

Use it when you need to answer:
- what can be repaired later
- what should be retried
- what needs manual review

### 5. Read-model truth
This is what the frontend reads for access decisions.

Owned by:
- `EntitlementGrant`

Use it when you need to answer:
- can the user access this bundle/content now?

## Object boundaries

### `CustomerSubscription`
Owns:
- current subscription lifecycle status
- provider ids
- current period
- cancellation flags
- checkout/lifecycle timestamps

Does not own:
- entitlement
- alerting
- raw provider incident detail

### `SubscriptionHistory`
Owns:
- the append-only story of subscription decisions

Does not own:
- current subscription state

### `Invoice`
Owns:
- billing facts
- paid/failure state
- current-cycle evidence

Does not own:
- subscription lifecycle authority

### `WebhookIncident`
Owns:
- deduplicated operational incidents
- raw payload evidence
- incident status

Does not own:
- business repair state

### `AbnormalOrder`
Owns:
- actionable repair workflow
- reconcile status

Does not own:
- raw operational incident history

### `EntitlementGrant`
Owns:
- access projection

Does not own:
- source subscription truth

## Event family boundary

### Checkout family
`checkout.session.*`
- initializes or fails the checkout phase
- does not own lifecycle truth

### Subscription family
`customer.subscription.*`
- owns lifecycle truth
- updates the local subscription state machine

### Invoice family
`invoice.*`
- stores billing facts
- influences entitlement
- does not replace lifecycle truth

## Why the boundaries matter
If these layers are mixed together, the system becomes hard to repair.

The current design keeps:
- state changes monotonic
- audit trail separate
- incidents separate from business repair
- read models separate from source-of-truth rows

This is the reason the system can be debugged and reconciled later.

## Short summary
State truth, audit truth, alert truth, repair truth, and read-model truth are separated on purpose. Do not flatten them into one table or one service.
