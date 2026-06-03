# Register user for commerce error checks
POST {{host}}/api/users
{
  "user": {
    "username": "ce_{{uid}}",
    "email": "ce_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"

# Protected commerce endpoints should reject missing auth
GET {{host}}/api/subscription/me
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists

GET {{host}}/api/subscription/options?provider=STRIPE
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists

GET {{host}}/api/entitlement/me
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists

GET {{host}}/api/entitlement/bundles/access
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists

POST {{host}}/api/payment/createOrder?provider=STRIPE&productId=1
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists

POST {{host}}/api/payment/createSubscription?provider=STRIPE&planCode=CREATOR_PRO_MONTHLY
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists

# Invalid provider for catalog should be rejected
GET {{host}}/api/subscription/options?provider=UNKNOWN
Authorization: Bearer {{token}}
HTTP 500
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.code" == 5001
jsonpath "$.message" == "System error"

# Invalid product id should be rejected
POST {{host}}/api/payment/createOrder?provider=STRIPE&productId=999999
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.code" == 5002
jsonpath "$.message" == "Invalid input"

# Invalid subscription plan should be rejected
POST {{host}}/api/payment/createSubscription?provider=STRIPE&planCode=NOT_A_PLAN
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.code" == 7105
jsonpath "$.message" == "subscription plan not found"

# Invalid entitlement checks should still return a shaped response
GET {{host}}/api/entitlement/content/not-real/access
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.contentId" == "not-real"
