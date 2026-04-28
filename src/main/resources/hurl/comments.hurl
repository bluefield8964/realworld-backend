# Setup: Register
POST {{host}}/api/users
{
  "user": {
    "username": "cmt_{{uid}}",
    "email": "cmt_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.token"

# Setup: Create article
POST {{host}}/api/articles
Authorization: Bearer {{token}}
{
  "article": {
    "title": "Comment Article {{uid}}",
    "description": "For comments",
    "body": "Article body"
  }
}
HTTP 201
[Captures]
slug: jsonpath "$.data.article.slug"

# Create comment
POST {{host}}/api/articles/{{slug}}/comments
Authorization: Bearer {{token}}
{
  "comment": {
    "body": "Test comment body"
  }
}
HTTP 201
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.comment.id" isInteger
jsonpath "$.data.comment.body" == "Test comment body"
jsonpath "$.data.comment.createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.comment.updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.comment.author.username" == "cmt_{{uid}}"
[Captures]
comment_id: jsonpath "$.data.comment.id"

# List comments
GET {{host}}/api/articles/{{slug}}/comments
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.comments" isList
jsonpath "$.data.comments" count == 1
jsonpath "$.data.comments[0].id" == {{comment_id}}
jsonpath "$.data.comments[0].body" == "Test comment body"
jsonpath "$.data.comments[0].createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.comments[0].updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.comments[0].author.username" == "cmt_{{uid}}"

# List comments without auth
GET {{host}}/api/articles/{{slug}}/comments
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.comments" isList
jsonpath "$.data.comments" count == 1
jsonpath "$.data.comments[0].id" isInteger
jsonpath "$.data.comments[0].body" == "Test comment body"
jsonpath "$.data.comments[0].createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.comments[0].updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.comments[0].author.username" == "cmt_{{uid}}"

# Delete comment
DELETE {{host}}/api/articles/{{slug}}/comments/{{comment_id}}
Authorization: Bearer {{token}}
HTTP 204

# Verify deletion
GET {{host}}/api/articles/{{slug}}/comments
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.comments" count == 0

# Selective deletion: create two comments, delete one, verify the other remains
POST {{host}}/api/articles/{{slug}}/comments
Authorization: Bearer {{token}}
{
  "comment": {
    "body": "First comment"
  }
}
HTTP 201
[Captures]
first_comment_id: jsonpath "$.data.comment.id"

POST {{host}}/api/articles/{{slug}}/comments
Authorization: Bearer {{token}}
{
  "comment": {
    "body": "Second comment"
  }
}
HTTP 201

# Verify two comments exist
GET {{host}}/api/articles/{{slug}}/comments
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.comments" count == 2

# Delete the first comment
DELETE {{host}}/api/articles/{{slug}}/comments/{{first_comment_id}}
Authorization: Bearer {{token}}
HTTP 204

# Verify only the second comment remains
GET {{host}}/api/articles/{{slug}}/comments
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.comments" count == 1
jsonpath "$.data.comments[0].body" == "Second comment"

# Cleanup
DELETE {{host}}/api/articles/{{slug}}
Authorization: Bearer {{token}}
HTTP 204

