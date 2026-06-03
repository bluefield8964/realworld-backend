# Post comment no auth
POST {{host}}/api/articles/some-slug/comments
{
  "comment": {
    "body": "test"
  }
}
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString

# Delete comment no auth
DELETE {{host}}/api/articles/some-slug/comments/1
HTTP 401
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString

# Setup: Register + create article
POST {{host}}/api/users
{
  "user": {
    "username": "ec_{{uid}}",
    "email": "ec_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"

POST {{host}}/api/articles
Authorization: Bearer {{token}}
{
  "article": {
    "title": "Err Comment Art {{uid}}",
    "description": "test",
    "body": "test"
  }
}
HTTP 201
[Captures]
slug: jsonpath "$.data.article.slug"

# Post comment empty body (global BizException handler)
POST {{host}}/api/articles/{{slug}}/comments
Authorization: Bearer {{token}}
{
  "comment": {
    "body": ""
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" == 5002
jsonpath "$.message" == "Invalid input"

# Post comment on unknown article
POST {{host}}/api/articles/unknown-slug-{{uid}}/comments
Authorization: Bearer {{token}}
{
  "comment": {
    "body": "orphan"
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" == 3001
jsonpath "$.message" == "Article not found"

# Get comments on unknown article
GET {{host}}/api/articles/unknown-slug-{{uid}}/comments
HTTP 200
[Asserts]
jsonpath "$.code" == 3001
jsonpath "$.message" == "Article not found"

# Delete comment on unknown article
DELETE {{host}}/api/articles/unknown-slug-{{uid}}/comments/99999
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" == 3001
jsonpath "$.message" == "Article not found"

# Delete non-existent comment on existing article
DELETE {{host}}/api/articles/{{slug}}/comments/99999
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" == 3000
jsonpath "$.message" == "Article not found"

# Cleanup
DELETE {{host}}/api/articles/{{slug}}
Authorization: Bearer {{token}}
HTTP 204
