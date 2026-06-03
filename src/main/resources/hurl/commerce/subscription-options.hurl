# Setup: Register
POST {{host}}/api/users
{
  "user": {
    "username": "opt_{{uid}}",
    "email": "opt_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"

# Read available subscription catalog for current provider
GET {{host}}/api/subscription/options?provider=STRIPE
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.provider" == "STRIPE"
jsonpath "$.data.plans" isList
jsonpath "$.data.plans" count >= 1
jsonpath "$.data.plans[0].planCode" isString
jsonpath "$.data.plans[0].provider" == "STRIPE"
jsonpath "$.data.plans[0].providerPriceId" isString
