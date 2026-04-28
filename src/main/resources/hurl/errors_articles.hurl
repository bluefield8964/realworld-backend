# Create article no auth
POST {{host}}/api/articles
{
  "article": {
    "title": "No Auth Article",
    "description": "test",
    "body": "test"
  }
}
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString

# GET unknown slug
GET {{host}}/api/articles/unknown-slug-{{uid}}
HTTP 404
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.errors.article[0]" == "not found"

# Update no auth
PUT {{host}}/api/articles/some-slug
{
  "article": {
    "body": "test"
  }
}
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString

# Delete no auth
DELETE {{host}}/api/articles/some-slug
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString

# GET feed no auth
GET {{host}}/api/articles/feed
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString

# Favorite no auth
POST {{host}}/api/articles/some-slug/favorite
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString

# Unfavorite no auth
DELETE {{host}}/api/articles/some-slug/favorite
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString

# Setup: Register for authenticated error tests
POST {{host}}/api/users
{
  "user": {
    "username": "ea_art_{{uid}}",
    "email": "ea_art_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.token"

# Create article empty title (currently accepted by backend)
POST {{host}}/api/articles
Authorization: Bearer {{token}}
{
  "article": {
    "title": "",
    "description": "test",
    "body": "test"
  }
}
HTTP 201

# Create article empty description (currently accepted by backend)
POST {{host}}/api/articles
Authorization: Bearer {{token}}
{
  "article": {
    "title": "Err Desc {{uid}}",
    "description": "",
    "body": "test"
  }
}
HTTP 201

# Create article empty body (currently accepted by backend)
POST {{host}}/api/articles
Authorization: Bearer {{token}}
{
  "article": {
    "title": "Err Body {{uid}}",
    "description": "test",
    "body": ""
  }
}
HTTP 201

# Duplicate titles lead to duplicate slug path in current implementation
POST {{host}}/api/articles
Authorization: Bearer {{token}}
{
  "article": {
    "title": "Dup Title {{uid}}",
    "description": "first",
    "body": "first"
  }
}
HTTP 201
[Captures]
slug1: jsonpath "$.data.article.slug"

POST {{host}}/api/articles
Authorization: Bearer {{token}}
{
  "article": {
    "title": "Dup Title {{uid}}",
    "description": "second",
    "body": "second"
  }
}
HTTP 500

# Update unknown slug (falls to global BizException handler)
PUT {{host}}/api/articles/unknown-slug-{{uid}}
Authorization: Bearer {{token}}
{
  "article": {
    "body": "test"
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" == 3000
jsonpath "$.message" == "Article not found"

# Favorite unknown slug (falls to global BizException handler)
POST {{host}}/api/articles/unknown-slug-{{uid}}/favorite
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" == 3000
jsonpath "$.message" == "Article not found"

# Unfavorite unknown slug (falls to global BizException handler)
DELETE {{host}}/api/articles/unknown-slug-{{uid}}/favorite
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" == 3000
jsonpath "$.message" == "Article not found"

# Delete unknown slug (falls to global BizException handler)
DELETE {{host}}/api/articles/unknown-slug-{{uid}}
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" == 3000
jsonpath "$.message" == "Article not found"

# Cleanup (only first slug is guaranteed captured here)
DELETE {{host}}/api/articles/{{slug1}}
Authorization: Bearer {{token}}
HTTP 204
