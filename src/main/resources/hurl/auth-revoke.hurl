# Register user for refresh/revoke flow
POST {{host}}/api/users
{
  "user": {
    "username": "revoke_{{uid}}",
    "email": "revoke_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"
refresh_token: header "set-cookie" regex "refresh_token=([^;]+);"

# Refresh should rotate the access token and refresh cookie
POST {{host}}/api/users/refresh
Cookie: refresh_token={{refresh_token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.bearer" isString
jsonpath "$.data.user.bearer" not isEmpty
[Captures]
rotated_token: jsonpath "$.data.user.bearer"
rotated_refresh_token: header "set-cookie" regex "refresh_token=([^;]+);"

# Logout should revoke the current session
POST {{host}}/api/logout
Authorization: Bearer {{rotated_token}}
HTTP 204

# Old refresh token must no longer work after logout
POST {{host}}/api/users/refresh
Cookie: refresh_token={{refresh_token}}
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists

# Rotated refresh token should also be invalid after logout
POST {{host}}/api/users/refresh
Cookie: refresh_token={{rotated_refresh_token}}
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
