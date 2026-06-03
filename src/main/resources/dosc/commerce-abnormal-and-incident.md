# Commerce Abnormal and Incident Handling

## Purpose
This document explains the distinction between abnormal business records and webhook incidents.

These are related, but they are not the same thing.

## Two separate truth layers

### Abnormal records
Use abnormal records for:
- repairable business debt
- reconciliation
- follow-up work queues

Examples:
- missing order
- missing payment
- missing subscription
- retry budget exhausted
- provider terminal failure

### Webhook incidents
Use incidents for:
- diagnostics
- alerting
- dedupe
- operational traceability

Examples:
- parse failure
- signature verification failure
- unknown system exception
- subscription history missing
- provider terminal failure

## Why the split matters
If every failure becomes an incident, you lose the repair workflow.
If every failure becomes abnormal, you lose operational visibility.

The current design keeps both:
- incident = something happened, observe it
- abnormal = something can be repaired, fix it

## Domain classification
`AbnormalDomainType` splits the world into:
- `ORDER`
- `SUBSCRIPTION`
- `INVOICE`
- `SYSTEM`

This is used to route the follow-up logic.

## Abnormal order types
The current `AbnormalOrderType` includes:
- `ORDER_MISSING`
- `PAYMENT_MISSING`
- `SUBSCRIPTION_MISSING`
- `SUBSCRIPTION_HISTORY_CHANGE_FAIL`
- `SUBSCRIPTION_STATUS_CHANGE_FAIL`
- `EVENT_RETRY_BUDGET_EXHAUSTED`
- `PROVIDER_TERMINAL_FAILURE`
- `PRE_BUSINESS_STUCK`

These are repair-oriented categories.

## Webhook incident types
The current `WebhookIncidentType` includes:
- `PROVIDER_TERMINAL_FAILURE`
- `PRE_BUSINESS_PARSE_FAILED`
- `PAYLOAD_PARSE_FAILED`
- `SIGNATURE_VERIFICATION_FAILED`
- `EVENT_ALREADY_DEAD`
- `EVENT_RETRY_BUDGET_EXHAUSTED`
- `SUBSCRIPTION_HISTORY_MISSING`
- `UNCLASSIFIED_SYSTEM_EXCEPTION`

These are alert/diagnostic categories.

## Escalation policy
`PaymentFailureEscalationService` is the policy center.

It decides:
- whether an exception should write a `WebhookIncident`
- whether the same failure should also write or update an `AbnormalOrder`
- which abnormal type should be used

### Pre-business failures
Failures before a usable business correlation key exists are treated differently.
They are usually incident-first, not abnormal-first.

### Business-ready failures
Once the system has a correlation key such as `orderNo`, `subscriptionNo`, or provider object id, failures can become repairable abnormal records.

## Pre-business stuck detection
The module also escalates repeated pre-business failures into a synthetic abnormal record.

This is used when:
- the same dedupe key repeats too often in a short window
- or the same failure remains stuck too long

The purpose is to prevent endless warning noise that never becomes actionable.

## Repair services

### Order repair
`OrderAbnormalService`:
- retrieves the provider checkout session
- validates order metadata and amount
- reconciles missing order or payment records
- moves abnormal records through `RECONCILING`, `FIXED`, `UNPAID_CONFIRMED`, `MANUAL_REVIEW`, or `EXHAUSTED`

### Subscription repair
`SubscriptionAbnormalService`:
- retrieves provider subscription or invoice
- resolves local subscription by business key or provider id
- backfills provider ids and current billing facts
- classifies mismatch, missing identity, or unsupported provider status cases

### Stalled subscription repair
`SubscriptionStalledReconcileService`:
- scans stale `PENDING` subscriptions
- scans stale `PAYING` subscriptions
- logs incidents for unrecoverable pending cases
- retries provider truth for paying cases
- applies the state machine to repair local state
- refreshes entitlement after a successful fix

## Status lifecycle of abnormal records
`AbnormalOrderStatus` currently supports:
- `PENDING`
- `RECONCILING`
- `FIXED`
- `UNPAID_CONFIRMED`
- `EXHAUSTED`
- `MANUAL_REVIEW`

Legacy enum values are still kept for older rows.

## Status lifecycle of incidents
`WebhookIncidentStatus` currently supports:
- `OPEN`
- `ACKED`
- `RESOLVED`

`WebhookIncidentOrchestrator` deduplicates incidents with a stable dedupe key and merges repeated occurrences into the same record.

## Boundary rule
Incident writes must never block state progression.
Abnormal writes should be explicit and repair-oriented.

If a write fails, the system should keep the main flow observable and continue retrying through the event pipeline.

## Short summary
Abnormal records are repair work. Incidents are operational evidence. Keep them separate.
