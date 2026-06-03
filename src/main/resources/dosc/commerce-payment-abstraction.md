# Commerce Payment Abstraction

## Purpose
This file explains how the payment layer is abstracted so the module is not locked to Stripe-specific code.

## Why abstraction exists
The project currently uses Stripe, but the code is written around a provider-neutral interface.
That is what makes the module extensible.

If another provider is added later, most of the module should stay unchanged.
Only the provider adapter and provider-specific mappings should change.

## The abstraction boundary

### `PaymentChannel`
This is the provider contract.

It defines:
- provider name
- checkout session creation for one-time orders
- checkout session creation for subscriptions
- raw webhook parsing
- provider object retrieval

Relevant methods:
- `createCheckoutSession(Order order)`
- `createSubscriptionSession(CustomerSubscription subscription)`
- `parseWebhook(...)`
- `parseRawEvent(...)`
- `retrieveSession(...)`
- `retrieveSubscription(...)`
- `retrieveInvoice(...)`

### `PaymentChannelRouter`
This class chooses the provider implementation by provider name.
It keeps the rest of the code from depending on `StripePaymentChannel` directly.

### `StripePaymentChannel`
This is the current provider implementation.
It translates provider-neutral business objects into Stripe API calls and Stripe objects back into provider-neutral models.

## Provider-neutral models

The adapter layer uses neutral model wrappers such as:
- `ProviderRawEvent`
- `ProviderEvent`
- `ProviderSession`
- `ProviderSubscription`
- `ProviderInvoice`
- `CheckoutSessionData`

These wrappers hide provider SDK details from the rest of the module.

## Stripe implementation shape

### One-time order checkout
`createCheckoutSession(Order order)`:
- uses payment mode
- sets success and cancel URLs from configuration
- writes order metadata into Stripe session metadata
- uses the order number as Stripe idempotency key

### Subscription checkout
`createSubscriptionSession(CustomerSubscription subscription)`:
- uses subscription mode
- maps plan code to provider price id
- writes subscription metadata into Stripe session and subscription metadata
- uses the subscription number as Stripe idempotency key
- configures redirects from application properties

### Webhook parsing
`parseWebhook(...)` and `parseRawEvent(...)`:
- verify Stripe signature
- convert Stripe SDK event into provider-neutral event objects

### Provider retrieval
`retrieveSession(...)`
`retrieveSubscription(...)`
`retrieveInvoice(...)`

These methods are critical for reconcile and abnormal repair.

## Why metadata matters
The adapter writes correlation fields into Stripe metadata so later webhook events can be mapped back to local business objects.

Important fields used in this project:
- `orderNo`
- `subscriptionNo`
- `userId`
- `planCode`
- `product`

## Error semantics
Stripe exceptions are normalized into `PaymentChannelException`.

The adapter classifies:
- retryable transport or infrastructure failures
- terminal credential or request-shape failures
- card decline codes that are retryable or hard terminal

This allows the rest of the module to decide:
- retry later
- accept and ignore
- write incident
- write abnormal record

## How to add another provider later
To add another provider such as PayPal:

1. implement `PaymentChannel`
2. register it in `PaymentChannelRouter`
3. provide provider-specific metadata and object mapping
4. keep the orchestration layer unchanged

The rest of the commerce module should stay provider-neutral.

## Short summary
`PaymentChannel` is the provider boundary. `StripePaymentChannel` is only one implementation of that contract.
