# GET unknown profile
GET {{host}}/api/profiles/unknown-user-{{uid}}
HTTP 404
[Asserts]
jsonpath "$.code" == 2003
jsonpath "$.message" == "Following user not found"

# Follow no auth
POST {{host}}/api/profiles/unknown-user-{{uid}}/follow
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString

# Unfollow no auth
DELETE {{host}}/api/profiles/unknown-user-{{uid}}/follow
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString

# Setup: Register for authenticated 404 tests
POST {{host}}/api/users
{
  "user": {
    "username": "ep_{{uid}}",
    "email": "ep_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"

# Follow unknown user (authed)
POST {{host}}/api/profiles/unknown-user-{{uid}}/follow
Authorization: Bearer {{token}}
HTTP 404
[Asserts]
jsonpath "$.code" == 2003
jsonpath "$.message" == "Following user not found"

# Unfollow unknown user (authed)
DELETE {{host}}/api/profiles/unknown-user-{{uid}}/follow
Authorization: Bearer {{token}}
HTTP 404
[Asserts]
jsonpath "$.code" == 2003
jsonpath "$.message" == "Following user not found"
