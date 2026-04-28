# Project General Architecture

## Purpose
- This file is a high-level orientation document for another GPT or engineer entering this repository.
- It explains the current module boundaries, the main runtime flows, and the architectural decisions that are already important in code.
- It is not meant to be a line-by-line spec. It should help a new reader quickly understand:
  - what this project is
  - where the main business domains live
  - how payment and subscription logic currently work
  - which parts are stable and which parts are still evolving

## Project Shape
- Package root: `realworld_backend`
- Current top-level bounded contexts:
  - `common`
  - `auth`
  - `article`
  - `commerce`

This is not a flat CRUD project anymore. It has already been reorganized around bounded contexts, and `commerce` is the most infrastructure-heavy module because it contains checkout creation, webhook ingestion, event idempotency, and abnormal reconciliation.

## Module Overview

### `common`
- Shared technical infrastructure and cross-cutting support code.
- Typical responsibilities:
  - shared exceptions
  - shared utilities
  - common web / framework helpers

### `auth`
- Authentication and identity-related application logic.
- This module is intentionally treated as its own local context instead of being scattered across common code.
- New auth-facing DTOs / commands are expected to stay local to the auth module unless they are truly shared.
- Current package layering is already explicit:
  - `api`
  - `application`
  - `controller`
  - `domain`
  - `infrastructure`
  - `repository`
  - `security`

### `article`
- RealWorld-style article, tag, profile, and user-facing content domain.
- This is closer to classic business CRUD compared with `commerce`.

### `commerce`
- Payment and subscription domain.
- Handles:
  - one-time order payments
  - subscription checkout creation
  - provider webhook ingestion
  - business event idempotency
  - abnormal order / subscription / invoice compensation

## Tools And Technology Stack

### Language and build
- Java `26`
- Maven
- Spring Boot `4.1.0-SNAPSHOT`

### Core backend framework
- Spring MVC
  - HTTP controller and request handling
- Spring Validation
  - request validation and grouped validation
- Spring Data JPA
  - repository abstraction and entity persistence

### Database and persistence
- MySQL connector
  - primary relational persistence connector currently declared in `pom.xml`
- Microsoft SQL Server JDBC
  - runtime connector also present in dependency set
- JPA / Hibernate style entity persistence
  - repository-driven persistence model across `article`, `auth`, and `commerce`

### Security and authentication tools
- Spring OAuth2 Resource Server starter
  - supports token-based protected endpoint handling
- JWT-oriented auth implementation
  - current auth infrastructure includes token-related utilities such as:
    - `TokenTool`
    - `JwtTokenService`
- cookie-based refresh token rotation
  - auth controller writes refresh token into HTTP-only secure cookies

### Payment and subscription tools
- Stripe Java SDK `24.0.0`
  - current payment provider integration
  - used for:
    - checkout session creation
    - webhook parsing / signature verification
    - subscription / invoice / session retrieve APIs
- provider abstraction layer
  - the code does not bind directly to Stripe everywhere
  - it uses:
    - `PaymentChannel`
    - `PaymentChannelRouter`
    - `StripePaymentChannel`

### Cache, lock, and concurrency tools
- Spring Data Redis
  - short-lived dedupe and lock helpers
- Redisson
  - distributed locking for webhook business processing and abnormal reconciliation
- RedisTemplate
  - lightweight submit lock, event key, and idempotency helpers

### Serialization and utility libraries
- Gson
  - JSON utility support
- Lombok
  - constructor generation, model boilerplate reduction, and logging annotations such as `@Slf4j`

### Testing and contract tools
- Spring test starters
  - JPA test support
  - MVC test support
- JUnit 5 and Mockito style test structure
  - used heavily in service-layer unit tests
- Hurl files under `src/main/resources/hurl`
  - request / response contract examples and manual API verification assets

### Operational patterns already used in code
- Redis short submit locks
- Redisson business locks
- SQL conditional state transitions
- scheduled abnormal reconcile job
- provider retrieve-based repair
- event idempotency rows in database

Another GPT should treat these as real architectural tools already used in the codebase, not as future wishlist items.

## Auth Runtime Entry Points

### HTTP API
- `POST /auth/users/login`
  - password login using email or username
- `POST /auth/users/register`
  - register and then auto-login
- `POST /auth/logout`
  - logout current authenticated session
- `POST /auth/users/refresh`
  - refresh-token rotation

### Main Controller
- `AuthController`
  - current HTTP entry for auth module
  - builds application commands from request input
  - sets / clears refresh-token cookies

## Auth Internal Layering

### 1. API Layer
- `auth.api.reqeust`
  - request DTOs and request-scoped helpers
  - examples:
    - `LoginRequest`
    - `CurrentAuthUser`
    - `IpResolver`
- `auth.api.response`
  - response DTOs
  - examples:
    - `LoginResponse`
    - `RefreshResponse`

This layer is for web-facing contract types, not domain objects.

### 2. Application Layer
- `auth.application.command`
  - command objects passed into use cases
  - examples:
    - `PasswordLoginCommand`
    - `LogoutCommand`
- `auth.application.result`
  - use-case output objects
  - example:
    - `LoginResult`
- `auth.application.useCase`
  - orchestration use cases
  - current main entry:
    - `PasswordLoginUseCase`
    - `PasswordLoginUseCaseImpl`

This layer coordinates auth flow but should not become provider/framework-heavy.

### 3. Domain Layer
- `auth.domain.model`
  - auth-specific business models
  - examples:
    - `UserAuthProfile`
    - `AuthSession`
    - `AuthRefreshToken`
    - `AuthAuditLog`
    - `TokenPair`
    - `LoginContext`
- `auth.domain.enumerous`
  - domain enums and identity-related value types
  - examples:
    - `AccountStatus`
    - `SessionStatus`
    - `LoginIdentifier`
    - `LoginIdentifierType`
- `auth.domain.exception`
  - auth-local domain exceptions
- `auth.domain.service`
  - domain service interfaces / policies
  - examples:
    - `PasswordVerifier`
    - `TokenService`
    - `SessionService`
    - `RiskService`
    - `AuditService`
    - `AuthRefreshTokenService`
    - `LoginAttemptService`
    - `AccountStatusChecker`
    - `UserAuthReader`
    - `MfaPolicy`
    - `policy.AccountPolicy`

This layer defines what auth needs, not how Spring or a concrete provider implements it.

### 4. Infrastructure Layer
- `auth.infrastructure.stub`
  - current concrete implementations are mostly stub/simple implementations
  - examples:
    - `JwtTokenService`
    - `InMemorySessionService`
    - `AuthRefreshTokenServiceImpl`
    - `SimpleLoginAttemptService`
    - `SimpleAccountStatusChecker`
    - `SpringPasswordVerifier`
    - `LogAuditService`
    - `NoopRiskService`
    - `NoopMfaPolicy`
    - `StubUserAuthService`
- `auth.infrastructure.stub.policyImpl`
  - current policy implementations such as `AccountPolicyImpl`

Important interpretation:
- the auth module is not a fake empty folder
- but it is also not yet a fully production-hardened auth platform
- many current implementations are intentionally simple or stub-oriented

### 5. Persistence Layer
- `auth.repository`
  - persistence gateways for auth-related aggregates
  - examples:
    - `UserAuthProfileRepository`
    - `AuthSessionRepository`
    - `AuthRefreshTokenRepository`
    - `AuthAuditLogRepository`

### 6. Security Utility Layer
- `auth.security`
  - auth/security helper utilities
  - current example:
    - `TokenTool`

## Auth Current Flow

### Password login flow
1. Client calls `POST /auth/users/login`.
2. `AuthController` validates request and resolves identifier type:
   - email login
   - username login
3. Controller builds `PasswordLoginCommand`.
4. `PasswordLoginUseCase` handles orchestration.
5. Use case depends on domain service interfaces such as:
   - user auth reader
   - password verifier
   - account policy / account status checker
   - login-attempt service
   - token service
   - session service
   - audit service
   - risk / MFA policy if needed
6. Successful login returns token pair and writes refresh-token cookie.

### Register flow
1. Client calls `POST /auth/users/register`.
2. Controller builds a registration-flavored `PasswordLoginCommand`.
3. Use case performs registration.
4. Then controller immediately triggers auto-login.
5. Final response returns login payload plus refresh-token cookie.

### Logout flow
1. Client calls `POST /auth/logout`.
2. Current authenticated user is injected through `@CurrentUser`.
3. Controller builds `LogoutCommand`.
4. Use case revokes or closes current session.
5. Refresh-token cookie is cleared.

### Refresh-token rotation flow
1. Client calls `POST /auth/users/refresh`.
2. Controller reads refresh token from cookie.
3. `PasswordLoginUseCase` performs token rotation.
4. New refresh token is written back into cookie.
5. New access token / refresh token pair is returned.

## Auth Design Intent
- The auth module is structured like a local clean-architecture style bounded context.
- Controller layer should stay thin.
- Application layer owns orchestration.
- Domain layer owns auth concepts and policy interfaces.
- Infrastructure layer provides concrete implementations.
- Repository layer provides persistence access.

Another GPT should understand that this auth module is already intentionally split by responsibility, even if some implementations are currently simple.

## Commerce Runtime Entry Points

### HTTP API
- `POST /api/payment/createOrder`
  - starts a one-time order checkout flow
- `POST /api/payment/createSubscription`
  - starts a subscription checkout flow
- `POST /api/webhook/stripeOrderAcceptor`
  - Stripe webhook ingress

### Main Controllers
- `PaymentController`
  - API entry for order and subscription checkout creation
- `WebhookController`
  - provider-facing webhook HTTP adapter
  - translates internal webhook decisions into HTTP 200 or retryable non-2xx responses

## Commerce Internal Layering

### 1. Checkout Creation Layer
- `OrderService`
  - creates or reuses one-time order checkout sessions
  - owns local `Order` creation and persistence
  - owns payment ledger initialization via `PaymentService`
- `SubscriptionService`
  - creates or reuses subscription checkout sessions
  - owns local `CustomerSubscription` creation and persistence

### 2. Provider Adapter Layer
- `PaymentChannel`
  - provider abstraction
  - checkout creation, webhook parsing, provider retrieve APIs
- `PaymentChannelRouter`
  - chooses the concrete provider adapter
- `StripePaymentChannel`
  - current Stripe implementation

### 3. Webhook Orchestration Layer
- `WebhookOrchestrator`
  - top-level pipeline coordinator
  - sequence:
    1. parse provider webhook
    2. normalize provider event type
    3. reserve event ownership
    4. route handler
    5. mark success or failure
    6. escalate terminal problems
- `EventReservationService`
  - event idempotency and retry-budget ownership logic
- `BusinessEventService`
  - persistence helper for event rows

### 4. Webhook Business Handler Layer
- `CheckoutSessionWebhookService`
  - handles `checkout.session.*`
  - contains both:
    - one-time order checkout completion/failure
    - subscription checkout completion/failure
- `SubscriptionWebhookService`
  - handles:
    - `customer.subscription.*`
    - `invoice.*`

### 5. Abnormal / Compensation Layer
- `AbnormalOrchestrator`
  - creates abnormal records
  - dispatches scheduled compensation work
- `OrderAbnormalService`
  - repairs one-time order anomalies
- `SubscriptionAbnormalService`
  - repairs subscription anomalies
  - repairs invoice anomalies
- `AbnormalOrderReconcileJob`
  - scheduled abnormal polling job

## Commerce Domain Model Groups

### Order / payment core
- `model`
  - order, payment, abnormal-order core entities

### Provider-neutral transport layer
- `model.core`
  - provider-neutral event and retrieve objects such as:
    - `ProviderRawEvent`
    - `ProviderSession`
    - `ProviderInvoice`
    - `ProviderSubscription`

### Checkout webhook payloads
- `model.checkoutPayment`
  - checkout-session webhook payload structures

### Invoice domain
- `model.invoice`
  - invoice entity and invoice payload structures

### Subscription domain
- `model.subscription`
  - `CustomerSubscription`
  - `SubscriptionHistory`
  - subscription plans / mapping / webhook payloads
- `model.subscription.enums`
  - subscription-only enums

## Key Business Flows

### One-time order payment flow
1. Client calls `POST /api/payment/createOrder`.
2. `OrderService.createOrReuseOrderCheckout(...)` takes a short Redis lock to avoid rapid duplicate submit.
3. `OrderService` builds an `activeKey` using `userId:productId`.
4. Important semantic:
   - `activeKey` is not a permanent "this product can only be bought once" key.
   - `activeKey` is an active checkout slot key.
   - It is used only to prevent parallel duplicate unpaid checkouts for the same user + product.
5. If an existing `PENDING` order already occupies that slot, it is reused.
6. Otherwise a new local `Order` is created.
7. `PaymentService.recordInit(...)` writes the local payment ledger.
8. `StripePaymentChannel.createCheckoutSession(...)` creates the checkout session.
9. Local order is moved to `PENDING`, and payment ledger is moved to `PAYING`.
10. Stripe later sends webhook events.
11. `CheckoutSessionWebhookService` handles order success or failure.
12. Final order success is protected by:
    - event-row idempotency
    - Redis event key
    - Redisson business lock
    - SQL conditional update like `markPaidIfNotPaid(...)`

### Subscription checkout flow
1. Client calls `POST /api/payment/createSubscription`.
2. `SubscriptionService.createOrReuseSubscriptionCheckout(...)` takes a short Redis lock using `userId:planCode`.
3. It looks for an existing local `CustomerSubscription`.
4. If existing subscription is still active now, checkout is rejected.
5. If existing subscription is `PENDING`, the old checkout URL is reused.
6. Otherwise a subscription checkout session is created through Stripe.
7. Local subscription is moved to `PENDING`.
8. Stripe later sends a combination of:
   - `checkout.session.*`
   - `customer.subscription.*`
   - `invoice.*`

### Subscription lifecycle truth sources
- `checkout.session.*`
  - used for checkout-stage success / failure and provider identity backfill
- `customer.subscription.*`
  - main lifecycle truth for activation / cancellation
- `invoice.*`
  - main truth for invoice state
  - used to update invoice records and limited recoverable subscription states
  - not treated as unconditional subscription truth anymore

## Key Identity Semantics

### WebhookContext semantics
- `trackingId`
  - business tracking key
  - examples:
    - order flow: `orderNo`
    - subscription flow: `subscriptionNo`
    - invoice flow: business subscription/order tracking key
- `providerTrackingId`
  - provider object unique key used for retrieve / reconcile
  - examples:
    - order checkout flow: `checkout session id`
    - subscription flow: `provider subscription id`
    - invoice flow: `invoice id`

This split is important and intentional. Another GPT should not collapse these two meanings.

### Subscription identity
- local business key: `subscriptionNo`
- provider key: `providerSubscriptionId` (`sub_xxx`)
- auxiliary provider key: `providerCustomerId` (`cus_xxx`)

### Invoice abnormal identity
- `orderNo`
  - business tracking key
- `sessionId`
  - provider invoice tracking key
  - invoice domain uses `sessionId = invoiceId (in_xxx)` inside abnormal records

## Webhook Processing Model

### Internal event model
- Provider raw events are converted into internal `BusinessEventType`.
- Unsupported events are normalized to `UNKNOWN_EVENT` and accepted with HTTP 200.

### Event reservation
- `EventReservationService.reserveOrTakeover(...)` ensures:
  - first-seen event inserts `PROCESSING`
  - already `SUCCEEDED` event is ignored idempotently
  - fresh `PROCESSING` owned by another worker retries later
  - stale `PROCESSING` or failed event can be taken over
  - exhausted event is marked `DEAD`

### HTTP semantics
- retryable webhook problems return non-2xx
- terminal webhook problems return HTTP 200
- this is controlled centrally by `WebhookErrorPolicy`

## Abnormal / Compensation Model

### Current intent
- `AbnormalOrder` is for anomalies that can be:
  - tracked
  - retried
  - reconciled
  - or eventually pushed to manual review

### Current processing states
- `PENDING`
- `RECONCILING`
- `FIXED`
- `MANUAL_REVIEW`
- `EXHAUSTED`
- `UNPAID_CONFIRMED`

### Current abnormal types
- represent original abnormal reason
- no longer try to double as processing status

### Lease-based reconcile model
- `RECONCILING` acts like a leased processing state
- stale `RECONCILING` rows can re-enter retry candidate selection
- this prevents permanent deadlock when a worker crashes mid-repair

### Current compensation strategy
- order anomalies:
  - repair through provider checkout-session retrieve
- subscription anomalies:
  - repair through provider subscription retrieve
- invoice anomalies:
  - repair through provider invoice retrieve

## Important Current Design Decisions

### Order purchases are repeatable
- same user can buy the same product again later
- `activeKey` only blocks concurrent unfinished checkout duplication
- `activeKey` is released when:
  - payment succeeds
  - checkout creation fails
  - payment enters retryable failed state
  - abnormal reconcile repairs order to paid

### Payment ledger writes are synchronous
- `recordInit(...)`
- `recordFail(...)`
- `recordPaying(...)`

These were intentionally made synchronous to reduce payment-row race conditions between checkout creation and webhook arrival.

### Invoice events no longer over-control subscription state
- `invoice.payment_failed`
  - no longer directly cancels subscription
  - only moves to a limited recoverable failure state such as `PAST_DUE`
- `invoice.payment_succeeded`
  - no longer revives any subscription from any state
  - only restores from specific recoverable states

## Current Known Gaps

### 1. System incidents and compensable abnormal orders are still partially mixed
- Some system-level webhook failures are still written into abnormal-order style flows.
- Long term, this project should probably split:
  - compensable `AbnormalOrder`
  - incident/alarm-style `WebhookIncident`

### 2. `SUBSCRIPTION_HISTORY_MISSING` is not fully closed yet
- It can be detected and recorded.
- But there is not yet a full dedicated repairer for subscription-history reconstruction.

### 3. Re-subscription semantics are still evolving
- Current design is closer to reusing an existing subscription record than creating a brand-new independent subscription contract every time.
- This should be treated as a business decision, not an accidental detail.

## How Another GPT Should Work In This Repo
- Do not assume old documentation is always current; verify against code.
- Treat `commerce` as a bounded context with multiple internal layers, not as one flat service package.
- Preserve the distinction between:
  - business tracking keys
  - provider tracking keys
  - abnormal reconcile keys
- When touching webhook code, think in this order:
  1. event normalization
  2. event reservation and idempotency
  3. business handler transition
  4. webhook HTTP retry semantics
  5. abnormal or incident escalation

## Best Follow-up Documents
- `src/main/resources/commerceModule.md`
  - current commerce runtime design
- `src/main/resources/commerce-maintenance-map.md`
  - class ownership / maintenance navigation map
