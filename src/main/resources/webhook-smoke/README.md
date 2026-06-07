# Webhook smoke fixtures

These JSON files are Stripe-shaped webhook payloads for local replay.

Source of truth for business metadata:
- `StripePaymentChannel#createSubscriptionSession(...)`
- `src/main/resources/data.sql` subscription plan seeds

Notes:
- `checkout.session.completed` uses the Checkout Session object fields Stripe sends in the event.
- `customer.subscription.*` fixtures carry subscription metadata on the Subscription object.
- `invoice.*` fixtures carry subscription business metadata under `subscription_details.metadata`, which matches the current invoice parser preference.
- All timestamps are Unix epoch seconds.
- All IDs are synthetic test IDs and can be reused across these fixtures for replay.

Suggested replay endpoint:

```http
POST /api/debug/webhook/replay?provider=STRIPE
```

