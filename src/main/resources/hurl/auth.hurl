# Register
POST {{host}}/api/users
{
  "user": {
    "username": "auth_{{uid}}",
    "email": "auth_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.username" == "auth_{{uid}}"
jsonpath "$.data.user.email" == "auth_{{uid}}@test.com"
jsonpath "$.data.user.bio" == null
jsonpath "$.data.user.image" == null
jsonpath "$.data.user.token" isString
jsonpath "$.data.user.token" not isEmpty
[Captures]
reg_token: jsonpath "$.data.user.token"

# Login
POST {{host}}/api/users/login
{
  "user": {
    "email": "auth_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.username" == "auth_{{uid}}"
jsonpath "$.data.user.email" == "auth_{{uid}}@test.com"
jsonpath "$.data.user.bio" == null
jsonpath "$.data.user.image" == null
jsonpath "$.data.user.token" isString
jsonpath "$.data.user.token" not isEmpty
[Captures]
token: jsonpath "$.data.user.token"

# Get current user
GET {{host}}/api/user
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.username" == "auth_{{uid}}"
jsonpath "$.data.user.email" == "auth_{{uid}}@test.com"
jsonpath "$.data.user.bio" == null
jsonpath "$.data.user.image" == null
jsonpath "$.data.user.token" isString
jsonpath "$.data.user.token" not isEmpty

# Update user
PUT {{host}}/api/user
Authorization: Bearer {{token}}
{
  "user": {
    "bio": "Updated bio"
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.username" == "auth_{{uid}}"
jsonpath "$.data.user.email" == "auth_{{uid}}@test.com"
jsonpath "$.data.user.bio" == "Updated bio"
jsonpath "$.data.user.image" == null
jsonpath "$.data.user.token" isString
jsonpath "$.data.user.token" not isEmpty

# Verify update persisted
GET {{host}}/api/user
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.username" == "auth_{{uid}}"
jsonpath "$.data.user.email" == "auth_{{uid}}@test.com"
jsonpath "$.data.user.bio" == "Updated bio"
jsonpath "$.data.user.image" == null
jsonpath "$.data.user.token" isString
jsonpath "$.data.user.token" not isEmpty

# Update user bio to empty string - should normalize to null
PUT {{host}}/api/user
Authorization: Bearer {{token}}
{
  "user": {
    "bio": ""
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.bio" == null

# Verify empty string normalization persisted
GET {{host}}/api/user
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.bio" == null

# Restore bio then set to null
PUT {{host}}/api/user
Authorization: Bearer {{token}}
{
  "user": {
    "bio": "Temporary bio"
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.bio" == "Temporary bio"

# Update user bio to null - should accept for nullable field
PUT {{host}}/api/user
Authorization: Bearer {{token}}
{
  "user": {
    "bio": null
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.bio" == null

# Verify null bio persisted
GET {{host}}/api/user
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.bio" == null

# Restore bio
PUT {{host}}/api/user
Authorization: Bearer {{token}}
{
  "user": {
    "bio": "Updated bio"
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.username" == "auth_{{uid}}"
jsonpath "$.data.user.email" == "auth_{{uid}}@test.com"
jsonpath "$.data.user.bio" == "Updated bio"
jsonpath "$.data.user.image" == null
jsonpath "$.data.user.token" isString
jsonpath "$.data.user.token" not isEmpty

# Update user image
PUT {{host}}/api/user
Authorization: Bearer {{token}}
{
  "user": {
    "image": "https://example.com/photo.jpg"
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.image" == "https://example.com/photo.jpg"

# Verify image update persisted
GET {{host}}/api/user
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.image" == "https://example.com/photo.jpg"

# Update image to empty string - should normalize to null
PUT {{host}}/api/user
Authorization: Bearer {{token}}
{
  "user": {
    "image": ""
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.image" == null

# Verify image empty string normalization persisted
GET {{host}}/api/user
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.image" == null

# Set image then update to null - should accept for nullable field
PUT {{host}}/api/user
Authorization: Bearer {{token}}
{
  "user": {
    "image": "https://example.com/temp.jpg"
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.image" == "https://example.com/temp.jpg"

PUT {{host}}/api/user
Authorization: Bearer {{token}}
{
  "user": {
    "image": null
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.image" == null

# Verify null image persisted
GET {{host}}/api/user
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.image" == null

# Update username and email
PUT {{host}}/api/user
Authorization: Bearer {{token}}
{
  "user": {
    "username": "auth_{{uid}}_upd",
    "email": "auth_{{uid}}_upd@test.com"
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.username" == "auth_{{uid}}_upd"
jsonpath "$.data.user.email" == "auth_{{uid}}_upd@test.com"
jsonpath "$.data.user.bio" == "Updated bio"
jsonpath "$.data.user.image" == null
jsonpath "$.data.user.token" isString
jsonpath "$.data.user.token" not isEmpty
[Captures]
updated_token: jsonpath "$.data.user.token"

# Verify username/email update persisted
GET {{host}}/api/user
Authorization: Bearer {{updated_token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.user.username" == "auth_{{uid}}_upd"
jsonpath "$.data.user.email" == "auth_{{uid}}_upd@test.com"
jsonpath "$.data.user.bio" == "Updated bio"
jsonpath "$.data.user.image" == null
jsonpath "$.data.user.token" isString
jsonpath "$.data.user.token" not isEmpty

