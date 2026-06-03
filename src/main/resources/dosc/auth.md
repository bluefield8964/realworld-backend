# Auth Module

## Purpose
This document describes the live authentication surface of the backend.
It is written against the current code, not against the old RealWorld sample API.

The current auth module is intentionally conservative:
- one canonical public surface: `/api/**`
- legacy-compatible request/response shapes for old frontend and Hurl
- local exception advice so auth failures are easy to read and do not leak unnecessary detail

## Public HTTP surface

### Active endpoints
- `POST /api/users`
- `POST /api/users/login`
- `POST /api/users/refresh`
- `POST /api/logout`
- `GET /api/user`
- `PUT /api/user`

### Contract summary
- Register and login still accept the legacy RealWorld payload shape:
  - `{ "user": { ... } }`
- Response payloads expose the current access token as `bearer`
- Refresh token is rotated in an HTTP-only cookie
- Refresh cookie path is `/api/users/refresh`

## Why this module exists
The auth module is not only login and register.
It also owns:
- session issuance
- refresh token rotation
- logout
- current-user lookup
- profile update for the authenticated principal
- request validation and masked auth failure handling

The code deliberately keeps one canonical runtime surface instead of splitting the application into parallel `/auth/**` and `/api/**` auth entrypoints.

## Main classes

- `LegacyAuthController`
  - register
  - login
  - refresh
  - logout
  - legacy-compatible `ApiResponse` wrapping
- `UserController`
  - get current user
  - update current user
- `AuthControllerAdvice`
  - module-local exception mapping
  - JSON parse errors
  - validation errors
  - auth exception masking

## Request and response shape

### Register
`POST /api/users`

Request:
```json
{
  "user": {
    "username": "probe123",
    "email": "probe123@test.com",
    "password": "password123"
  }
}
```

### Login
`POST /api/users/login`

Request can use either:
- `email`
- `username`

The payload is still wrapped in `user`.

### Current user
`GET /api/user`

Returns:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "user": { ... }
  }
}
```

### Refresh
`POST /api/users/refresh`

Returns the same legacy response shape with:
- `data.user.bearer`

### Logout
`POST /api/logout`

Clears the refresh cookie and invalidates the session.

## Runtime flow

### Register / login
1. Controller parses the legacy `user` node.
2. Basic field extraction happens locally.
3. `PasswordLoginUseCase` performs the actual login or registration orchestration.
4. A `TokenPair` is returned.
5. Refresh token is written to the HTTP-only cookie.
6. Response payload returns `data.user.bearer`.

### Refresh
1. Controller reads the `refresh_token` cookie.
2. Missing cookie becomes a token-missing error.
3. `PasswordLoginUseCase.refreshTokenRotation(...)` rotates the token.
4. New refresh cookie is written.
5. Response payload returns the new access token in `data.user.bearer`.

### Logout
1. Controller reads the authenticated principal from the current-user argument.
2. `PasswordLoginUseCase.logout(...)` ends the session.
3. Refresh cookie is cleared.

## Error handling

### Localized advice
`AuthControllerAdvice` handles auth controller errors before the global fallback.

It captures:
- `HttpMessageNotReadableException`
- `MethodArgumentNotValidException`
- `AuthException`
- `BizException`
- generic `Exception`

### Why this is important
It keeps auth errors easy to diagnose and prevents unrelated modules from flattening everything into one generic response.

### Masked login failures
Login failures are intentionally masked for sensitive cases.

The frontend should not learn whether the account was:
- missing
- locked
- disabled
- banned
- rate-limited
- wrong password

For those cases, the response is normalized to a generic invalid-credentials error.

### JSON parsing errors
Malformed request JSON is handled explicitly as a JSON error.
This is important because auth requests use raw `JsonNode` parsing for the legacy payload shape.

## Relationship to other modules

- `article` still owns the user profile read model used by the current-user endpoint.
- `commerce` uses the current authenticated principal to create orders, subscriptions, and entitlement reads.
- `common` provides the response wrapper, security config, and the current-user resolver.

## Current limitations

- MFA is not fully closed as an end-to-end challenge flow.
- Some response handling is still legacy-compatible by design.
- The module is stable enough for the current frontend and Hurl contract, but it should still be treated as an evolving auth surface rather than a finished identity platform.

## Short summary
Auth is now a single legacy-compatible `/api/**` surface with refresh-cookie rotation, masked login failure semantics, and module-local exception handling.
