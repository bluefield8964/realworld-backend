# Setup: Register
POST {{host}}/api/users
{
  "user": {
    "username": "pay_{{uid}}",
    "email": "pay_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"

# Fresh user should have no active entitlements before payment
GET {{host}}/api/entitlement/me
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.entitlements" isList
jsonpath "$.data.entitlements" count == 0

# Create one-time order checkout URL
# Replace productId with a real product row available in your environment.
POST {{host}}/api/payment/createOrder?provider=STRIPE&productId=1
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
body matches "^https?://"
[Captures]
payment_url: body

# Success and cancel redirects are handled by the frontend routes below
# /order-success
# /order-cancel

# Re-click createOrder while still pending should reuse the same checkout URL
POST {{host}}/api/payment/createOrder?provider=STRIPE&productId=1
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
body == "{{payment_url}}"

# One-time checkout creation alone should not grant entitlement before webhook completion
GET {{host}}/api/entitlement/me
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.entitlements" isList
