# Commerce Entitlement

## Purpose
This document explains how subscription state becomes actual product access.

The entitlement layer is a projection, not a source of truth.

## The key rule
Do not use entitlement as the canonical subscription state.

Subscription truth lives in:
- `CustomerSubscription`
- `SubscriptionHistory`
- provider webhook facts

Entitlement only answers:
- can this user access this resource now?

## Main classes

- `EntitlementProjector`
  - converts subscription truth into entitlement grants
- `EntitlementStatusMachine`
  - decides the entitlement state from subscription + invoice facts
- `EntitlementFacade`
  - read-model facade used by controllers and other services
- `EntitlementQueryService`
  - answers access checks
- `EntitlementController`
  - read endpoints for entitlement and access checks

## Current access model

### Supported entitlement statuses
The enum currently includes:
- `ACTIVE`
- `GRACE`
- `REVOKED`
- `EXPIRED`

The current runtime flow mainly relies on:
- `ACTIVE`
- `REVOKED`
- `EXPIRED`

`GRACE` exists as a reserved status in the enum, but the current subscription projection logic does not depend on it as a first-class step.

## How the entitlement decision is made

`EntitlementStatusMachine` evaluates the subscription using:
- current subscription status
- current period start/end
- current time
- invoice facts for the current cycle

### High-level decision rules
- `TRIALING`
  - grant access directly while not expired
- `ACTIVE`
  - grant access only if the current-cycle invoice is grantable
- `PAST_DUE`
  - revoke access
- `PAUSED`
  - revoke access
- `CHECKOUT_FAIL`, `CHECKOUT_EXPIRED`, `INITIAL_FAIL`, `PENDING`, `PAYING`
  - revoke access
- `CANCELED`
  - if the current period already ended -> `EXPIRED`
  - if the current period is still valid and the current-cycle invoice is grantable -> keep `ACTIVE`
  - otherwise -> `REVOKED`

## Current-cycle invoice matching
The entitlement check does not simply look at the latest invoice blindly when period boundaries are available.

### Preferred rule
If `currentPeriodStart` and `currentPeriodEnd` exist:
- find an invoice whose period covers the current subscription cycle
- then check whether that invoice is grantable

### Fallback
If the current period window is missing:
- fall back to the latest invoice for that subscription

## What makes an invoice grantable
An invoice is considered grantable when any of these is true:
- `paid == true`
- `paymentStatus == SUCCESS`
- `amountDue == 0`

## Read-model access checks
`EntitlementQueryService` provides:
- `hasBundleAccess`
- `hasContentAccess`
- `hasAnyBundleAccess`
- `listActiveEntitlements`

It uses repository checks against active entitlement grants.

## Why projection is needed
The product should not read raw subscription and invoice tables directly for every access decision.
That would couple the content layer to provider lifecycle rules.

Instead:
1. subscription and invoice facts are updated
2. entitlement is projected
3. read APIs query the entitlement projection

This keeps access checks simple and fast.

## Controller endpoints
- `GET /api/entitlement/me`
- `GET /api/entitlement/bundles/access`
- `GET /api/entitlement/bundles/{bundleCode}/access`
- `GET /api/entitlement/content/{contentId}/access`

## Short summary
Entitlement is the access projection layer. It turns subscription and invoice truth into a user-facing access answer.
