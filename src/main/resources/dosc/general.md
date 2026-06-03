# Project General Architecture

## Purpose
This file is the repo-wide orientation doc.
It explains the live runtime shape of the backend, the major module boundaries, and the reading order for the detailed docs.

This is not a changelog. It should track the current code, not earlier intentions.

## One-line summary
This repository is a bounded-context Spring backend with:
- `auth` for identity and session management
- `article` for RealWorld-style content APIs
- `commerce` for Stripe checkout, subscription lifecycle, entitlement projection, webhook repair, and observability
- `common` for cross-cutting configuration and shared error handling

## Recommended reading order
1. `auth.md`
2. `commerce.md`
3. `commerce-truth-boundary.md`
4. `commerce-subscription-state.md`
5. `commerce-webhook-architecture.md`
6. `commerce-entitlement.md`
7. `commerce-abnormal-and-incident.md`
8. `commerce-payment-abstraction.md`
9. `commerce-observability.md`

## Module boundaries

- `common`
  - shared configuration, exception handling, security, Redis, Redisson, Stripe, web argument resolution
- `auth`
  - login, register, refresh, logout, session rotation, legacy compatibility auth surface
- `article`
  - articles, comments, tags, profiles, favorites, following, content read APIs
- `commerce`
  - one-time payment, subscription checkout, webhook orchestration, subscription lifecycle, invoice facts, entitlement projection, reconcile, metrics

## Design style
- Controllers are thin.
- Application services own orchestration.
- Domain/service interfaces express policy and business meaning.
- Infrastructure classes implement concrete providers and framework integration.
- Realtime flows are protected by:
  - event reservation / idempotency
  - Redis and Redisson locks
  - CAS-style conditional updates
  - state machines
  - retryable vs terminal decision policy

This is already close to a small enterprise backend in structure, even though some parts are still intentionally simple or still being tightened.

## Stack snapshot
- Java 26
- Spring Boot 3.5.x line
- Maven
- Spring MVC
- Spring Validation
- Spring Data JPA / Hibernate
- MySQL
- Redis
- Redisson
- Stripe Java SDK
- Actuator + Micrometer + Prometheus exposure
- JUnit 5 / Mockito
- Hurl contracts under `src/main/resources/hurl`

## Cross-cutting infrastructure

### Security and auth
- The repo uses the legacy compatibility auth surface: `/api/**`
- Refresh token rotation is cookie-based.
- Access token output is carried as `bearer` in the legacy compatibility surface.
- `AuthControllerAdvice`, `ArticleControllerAdvice`, and `CommerceControllerAdvice` are module-local exception boundaries.
- `GlobalExceptionHandler` is a fallback, not the only place where errors are understood.

### Observability
- `spring-boot-starter-actuator` is already wired.
- `/actuator/health`, `/actuator/info`, `/actuator/metrics`, and `/actuator/prometheus` are exposed.
- `CommerceMetricsService` records webhook, reconcile, subscription transition, and entitlement projection metrics.
- Prometheus should scrape `/actuator/prometheus`; Grafana should read Prometheus, not the app directly.

### Redis / locking
- Redis is used for short-lived idempotency and dedupe.
- Redisson is used for business locks around webhook processing and subscription reconciliation.
- Redisson now reads from Spring Redis properties instead of hard-coded host/port.

### Stripe redirects
- Checkout success/cancel URLs are configuration-driven.
- They should be treated as environment config, not code constants.

## Runtime flow summary

### Auth flow
1. Login/register hits the legacy auth controller.
2. Controller parses the legacy `user` payload.
3. Use case orchestrates password/session/risk/account checks.
4. Access token and refresh token are issued.
5. Refresh token is rotated in an HTTP-only cookie.

### Article flow
1. Controllers accept RealWorld-style content requests.
2. Services query or mutate article/profile/comment state.
3. Module-local advice decides how controller exceptions are rendered.

### Commerce flow
1. Client calls `createOrder` or `createSubscription`.
2. Local order/subscription row is created or reused.
3. Checkout session is created through the provider adapter.
4. Stripe webhook arrives later.
5. `WebhookOrchestrator` parses, reserves, routes, and finalizes the event.
6. Checkout / lifecycle / invoice services apply their own rules.
7. Entitlement is projected from subscription + invoice truth.
8. Reconcile jobs repair dead or stalled cases.

## Architecture assessment
This repository is already close to an enterprise-style backend in the following ways:
- bounded contexts exist and are respected
- controllers are thin
- there is a layered application/domain/infrastructure split in auth
- commerce has explicit adapter/orchestrator/state-machine boundaries
- webhook handling is idempotent and lock-aware
- there is a real reconcile path, not just happy-path webhook code
- metrics are built in, not bolted on later
- legacy compatibility is isolated instead of flattening the new API surface

What is still not fully enterprise-complete:
- MFA is not fully closed end-to-end
- profile management is still too local in config
- some article exception mapping is still duplicated
- some docs and tests still need a second pass to fully match the latest runtime behavior

## What another GPT should read first
1. `auth.md`
2. `commerce.md`
3. `commerce-truth-boundary.md`
4. current controller classes for auth / article / commerce
5. current Hurl files under `src/main/resources/hurl`
