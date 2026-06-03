# Register main user
POST {{host}}/api/users
{
  "user": {
    "username": "prof_{{uid}}",
    "email": "prof_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"

# Register celeb user
POST {{host}}/api/users
{
  "user": {
    "username": "celeb_{{uid}}",
    "email": "celeb_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201

# Get profile without auth
GET {{host}}/api/profiles/celeb_{{uid}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.profile.username" == "celeb_{{uid}}"
jsonpath "$.data.profile.bio" == null
jsonpath "$.data.profile.image" == null
jsonpath "$.data.profile.following" == false

# Get profile with auth
GET {{host}}/api/profiles/celeb_{{uid}}
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.profile.username" == "celeb_{{uid}}"
jsonpath "$.data.profile.bio" == null
jsonpath "$.data.profile.image" == null
jsonpath "$.data.profile.following" == false

# Follow profile
POST {{host}}/api/profiles/celeb_{{uid}}/follow
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.profile.username" == "celeb_{{uid}}"
jsonpath "$.data.profile.bio" == null
jsonpath "$.data.profile.image" == null
jsonpath "$.data.profile.following" == true

# Unfollow profile
DELETE {{host}}/api/profiles/celeb_{{uid}}/follow
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.profile.username" == "celeb_{{uid}}"
jsonpath "$.data.profile.bio" == null
jsonpath "$.data.profile.image" == null
jsonpath "$.data.profile.following" == false

# Verify unfollow persisted
GET {{host}}/api/profiles/celeb_{{uid}}
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.profile.username" == "celeb_{{uid}}"
jsonpath "$.data.profile.bio" == null
jsonpath "$.data.profile.image" == null
jsonpath "$.data.profile.following" == false

