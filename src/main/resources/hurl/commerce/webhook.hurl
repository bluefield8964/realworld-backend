# Manual Stripe webhook regression check.
# This request requires a real Stripe-Signature generated with the local endpoint secret.
# Replace the payload and signature with a captured event from Stripe CLI before running.

POST {{host}}/api/webhook/stripeOrderAcceptor
Stripe-Signature: {{stripe_signature}}
Content-Type: application/json
{
  "id": "evt_test_001",
  "type": "checkout.session.completed",
  "data": {
    "object": {
      "id": "cs_test_001"
    }
  }
}
HTTP 200
[Asserts]
body matches "accepted_terminal|retry_later"
