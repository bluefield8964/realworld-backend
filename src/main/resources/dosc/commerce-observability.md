# Commerce Observability

## Purpose
This document explains what to watch in production and how the commerce module exposes metrics.

## Built-in telemetry
The backend already exposes Actuator and Prometheus endpoints.

Relevant endpoints:
- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

`Prometheus` should scrape `/actuator/prometheus`.
`Grafana` should read Prometheus, not the application directly.

## Metrics service
`CommerceMetricsService` currently records:
- webhook received count
- webhook result count
- webhook duration
- subscription transition count
- entitlement projection count
- reconcile scanned count
- reconcile fixed count
- reconcile incident count
- reconcile duration

## Important metric names
- `commerce_webhook_received_total`
- `commerce_webhook_result_total`
- `commerce_webhook_duration_seconds`
- `commerce_subscription_transition_total`
- `commerce_entitlement_projection_total`
- `commerce_reconcile_scanned_total`
- `commerce_reconcile_fixed_total`
- `commerce_reconcile_incident_total`
- `commerce_reconcile_duration_seconds`

## What these metrics help answer
- Are webhooks arriving?
- Are they being accepted or rejected?
- Are subscription transitions moving forward?
- Is entitlement projection keeping up?
- Are reconcile jobs scanning too many stalled records?
- Is repair succeeding or ending in manual review?

## Suggested dashboard panels

### Webhook health
- received per minute
- result breakdown
- duration p95 / p99

### Subscription health
- transition counts by from/to status
- stalled `PENDING` count
- stalled `PAYING` count

### Entitlement health
- projection count by status
- access grants vs revocations

### Reconcile health
- scanned count
- fixed count
- incident count
- duration

## Suggested alert ideas
- webhook retry or terminal failures are spiking
- stalled `PENDING` subscriptions are growing
- stalled `PAYING` subscriptions are growing
- reconcile incident count is rising faster than fixed count
- entitlement projections suddenly drop
- webhook duration becomes unstable

## Practical note
Metrics are not logs.
They are aggregated time-series facts.

Use logs for:
- one-off debugging
- raw error stack traces
- exact payload inspection

Use metrics for:
- trends
- alerts
- SLO-ish checks

## Short summary
The commerce module is already instrumented. Prometheus and Grafana are the next step for operational visibility, not a prerequisite for correctness.
