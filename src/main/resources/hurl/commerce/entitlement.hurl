# Setup: Register
POST {{host}}/api/users
{
  "user": {
    "username": "ent_{{uid}}",
    "email": "ent_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"

# Current user entitlement list should be empty for a fresh user
GET {{host}}/api/entitlement/me
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.entitlements" isList
jsonpath "$.data.entitlements" count == 0

# Fresh user should not have any bundle access
GET {{host}}/api/entitlement/bundles/access
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.hasAccess" == false

# Fresh user should not have a specific bundle access
GET {{host}}/api/entitlement/bundles/CREATOR_PRO_BUNDLE/access
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.bundleCode" == "CREATOR_PRO_BUNDLE"
jsonpath "$.data.hasAccess" == false

# Fresh user should not have content access
GET {{host}}/api/entitlement/content/creator_1001/access
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.contentId" == "creator_1001"
jsonpath "$.data.hasAccess" == false
