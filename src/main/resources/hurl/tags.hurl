# Setup: Register
POST {{host}}/api/users
{
  "user": {
    "username": "tag_{{uid}}",
    "email": "tag_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"

# Setup: Create article with tags
POST {{host}}/api/articles
Authorization: Bearer {{token}}
{
  "article": {
    "title": "Tag Article {{uid}}",
    "description": "For tags",
    "body": "Article body",
    "tagList": ["h_{{uid}}", "t_{{uid}}"]
  }
}
HTTP 201
[Captures]
slug: jsonpath "$.data.article.slug"

# Get tags
GET {{host}}/api/tags
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.tags" isList
jsonpath "$.data.tags" count >= 1
jsonpath "$.data.tags" contains "h_{{uid}}"
jsonpath "$.data.tags" contains "t_{{uid}}"
jsonpath "$.data.tags[0]" isString
jsonpath "$.data.tags[1]" isString

# Cleanup
DELETE {{host}}/api/articles/{{slug}}
Authorization: Bearer {{token}}
HTTP 204

