# Setup: Register
POST {{host}}/api/users
{
  "user": {
    "username": "sub_{{uid}}",
    "email": "sub_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"

# Fresh user should start without subscription
GET {{host}}/api/subscription/me
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.subscription.hasSubscription" == false
jsonpath "$.data.subscription.accessGranted" == false
jsonpath "$.data.subscription.subscriptionNo" == null
jsonpath "$.data.subscription.status" == null

# Fresh user should start without bundle access
GET {{host}}/api/entitlement/bundles/access
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.hasAccess" == false

# Create subscription checkout URL
# Replace planCode with a real SubscriptionPlan.planCode available in your environment.
POST {{host}}/api/payment/createSubscription?provider=STRIPE&planCode=CREATOR_PRO_MONTHLY
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
body matches "^https?://"
[Captures]
subscription_url: body

# Frontend redirect targets used after checkout completion:
# /success?session_id={CHECKOUT_SESSION_ID}
# /cancel

# Re-click createSubscription while still pending should reuse the same checkout URL
POST {{host}}/api/payment/createSubscription?provider=STRIPE&planCode=CREATOR_PRO_MONTHLY
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
body == "{{subscription_url}}"

# Read current user's subscription view after checkout creation
GET {{host}}/api/subscription/me
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.subscription" exists
jsonpath "$.data.subscription.hasSubscription" == true
jsonpath "$.data.subscription.accessGranted" == false
jsonpath "$.data.subscription.subscriptionNo" isString
jsonpath "$.data.subscription.status" == "PENDING"

# Checkout creation alone should not grant bundle access before webhook progression
GET {{host}}/api/entitlement/bundles/access
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.hasAccess" == false

# Entitlement list should still be empty before subscription lifecycle/invoice confirmation
GET {{host}}/api/entitlement/me
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.entitlements" isList
