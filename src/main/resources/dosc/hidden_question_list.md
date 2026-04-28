# Hidden Question List

## Naming and structure

- `auth.api.reqeust` is misspelled across the auth API package. It should become `auth.api.request`, but that rename touches many imports and package declarations, so I left it for a dedicated refactor.
- `userController`, `WebInsolverConfig`, `UserInsolverImpl`, and `logAspectConfig` all break the repo's dominant Java naming style. They compile, but they add friction for navigation and maintenance.
- `src/main/resources/dosc` itself looks like a typo of `docs`. If this path is now public or referenced by tooling, rename carefully to avoid broken links.
- `subscription_historys` is also a typo, but it matches the live database table you provided. The code should keep matching the table unless you plan a DB migration.

## Entity and schema alignment questions

- The database has a `stripe_events` table, but the current JPA model does not expose a matching entity. The commerce code appears to persist webhook/event state in `business_events` instead. Confirm whether `stripe_events` is a legacy table, a table managed outside JPA, or a missing entity mapping.
- There is an entity at `realworld_backend.common.exception.Error` mapped to table `error`, but `error` is not in your provided schema list. Confirm whether this entity is obsolete, test-only, or pointing at a table that should exist.

## Runtime and platform risks

- `spring.jpa.hibernate.ddl-auto=update` is active in both `application.yaml` and `application-local.yaml`. That is convenient for development, but it lets entity edits mutate the schema implicitly at startup. If you want repeatable migrations, this should move to Flyway/Liquibase or be restricted to local-only use.
- `@SpringBootTest` startup logs show `spring.jpa.open-in-view` is still enabled by default. That can hide lazy-loading and transaction-boundary problems in web requests.
- The test runtime logs `jakarta.validation.NoProviderFoundException`, which means Bean Validation annotations are present but no validation provider is on the classpath. If request/entity validation is expected to enforce constraints, add a provider such as Hibernate Validator.

## Logic notes

- I fixed one concrete logic issue this round: `DefaultWebhookErrorPolicy` did not mark non-retryable `PaymentChannelException` cases for abnormal upsert, which contradicted the existing unit test and the intended manual-review branch.
- `AuthSession.isActive()` previously assumed `expiresAt` was never null and could throw a null-pointer exception on partially initialized sessions. I added a null guard, but it is still worth deciding whether `expiresAt` should be made mandatory at the model boundary.
