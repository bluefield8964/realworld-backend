# Setup: Register
POST {{host}}/api/users
{
  "user": {
    "username": "page_{{uid}}",
    "email": "page_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"

# Create article 1
POST {{host}}/api/articles
Authorization: Bearer {{token}}
{
  "article": {
    "title": "Pagination 1 {{uid}}",
    "description": "Page test 1",
    "body": "Page body 1"
  }
}
HTTP 201
[Captures]
slug1: jsonpath "$.data.article.slug"

# Create article 2
POST {{host}}/api/articles
Authorization: Bearer {{token}}
{
  "article": {
    "title": "Pagination 2 {{uid}}",
    "description": "Page test 2",
    "body": "Page body 2"
  }
}
HTTP 201
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.title" == "Pagination 2 {{uid}}"
jsonpath "$.data.article.slug" isString
[Captures]
slug2: jsonpath "$.data.article.slug"

# List with limit=1 (most recent first, so slug2)
GET {{host}}/api/articles?author=page_{{uid}}&limit=1
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.articles" count == 1
jsonpath "$.data.articlesCount" == 2
jsonpath "$.data.articles[0].slug" == "{{slug2}}"

# List with limit=1&offset=1 (second page, so slug1)
GET {{host}}/api/articles?author=page_{{uid}}&limit=1&offset=1
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.articles" count == 1
jsonpath "$.data.articlesCount" == 2
jsonpath "$.data.articles[0].slug" == "{{slug1}}"

# Cleanup
DELETE {{host}}/api/articles/{{slug1}}
Authorization: Bearer {{token}}
HTTP 204

DELETE {{host}}/api/articles/{{slug2}}
Authorization: Bearer {{token}}
HTTP 204

