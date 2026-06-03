# Setup: Register
POST {{host}}/api/users
{
  "user": {
    "username": "art_{{uid}}",
    "email": "art_{{uid}}@test.com",
    "password": "password123"
  }
}
HTTP 201
[Captures]
token: jsonpath "$.data.user.bearer"

# Create article with tags
POST {{host}}/api/articles
Authorization: Bearer {{token}}
{
  "article": {
    "title": "Test Article {{uid}}",
    "description": "Test description",
    "body": "Test body content",
    "tagList": ["d_{{uid}}", "t_{{uid}}"]
  }
}
HTTP 201
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.title" == "Test Article {{uid}}"
jsonpath "$.data.article.slug" isString
jsonpath "$.data.article.description" == "Test description"
jsonpath "$.data.article.body" == "Test body content"
jsonpath "$.data.article.tagList" contains "d_{{uid}}"
jsonpath "$.data.article.tagList" contains "t_{{uid}}"
jsonpath "$.data.article.createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T"
jsonpath "$.data.article.updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T"
jsonpath "$.data.article.favorited" == false
jsonpath "$.data.article.favoritesCount" == 0
jsonpath "$.data.article.author.username" == "art_{{uid}}"
[Captures]
slug: jsonpath "$.data.article.slug"
created_at: jsonpath "$.data.article.createdAt"
updated_at: jsonpath "$.data.article.updatedAt"

# List all articles
GET {{host}}/api/articles
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.articles" isList
jsonpath "$.data.articlesCount" isInteger
jsonpath "$.data.articlesCount" >= 1
jsonpath "$.data.articles[0].title" isString
jsonpath "$.data.articles[0].slug" isString
jsonpath "$.data.articles[0].description" isString
jsonpath "$.data.articles[0].body" not exists
jsonpath "$.data.articles[0].tagList" isList
jsonpath "$.data.articles[0].createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.articles[0].updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.articles[0].favorited" isBoolean
jsonpath "$.data.articles[0].favoritesCount" isInteger
jsonpath "$.data.articles[0].author.username" isString

# List by author
GET {{host}}/api/articles?author=art_{{uid}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.articles" isList
jsonpath "$.data.articlesCount" isInteger
jsonpath "$.data.articlesCount" >= 1
jsonpath "$.data.articles[0].title" isString
jsonpath "$.data.articles[0].slug" isString
jsonpath "$.data.articles[0].description" isString
jsonpath "$.data.articles[0].body" not exists
jsonpath "$.data.articles[0].tagList" isList
jsonpath "$.data.articles[0].createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.articles[0].updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.articles[0].favorited" isBoolean
jsonpath "$.data.articles[0].favoritesCount" isInteger
jsonpath "$.data.articles[0].author.username" == "art_{{uid}}"

# List all articles with auth
GET {{host}}/api/articles
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.articles" isList
jsonpath "$.data.articlesCount" isInteger
jsonpath "$.data.articlesCount" >= 1
jsonpath "$.data.articles[0].title" isString
jsonpath "$.data.articles[0].slug" isString
jsonpath "$.data.articles[0].description" isString
jsonpath "$.data.articles[0].body" not exists
jsonpath "$.data.articles[0].tagList" isList
jsonpath "$.data.articles[0].createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.articles[0].updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.articles[0].favorited" isBoolean
jsonpath "$.data.articles[0].favoritesCount" isInteger
jsonpath "$.data.articles[0].author.username" isString

# List by author with auth
GET {{host}}/api/articles?author=art_{{uid}}
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.articles" isList
jsonpath "$.data.articlesCount" isInteger
jsonpath "$.data.articlesCount" >= 1
jsonpath "$.data.articles[0].title" isString
jsonpath "$.data.articles[0].slug" isString
jsonpath "$.data.articles[0].description" isString
jsonpath "$.data.articles[0].body" not exists
jsonpath "$.data.articles[0].tagList" isList
jsonpath "$.data.articles[0].createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.articles[0].updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.articles[0].favorited" isBoolean
jsonpath "$.data.articles[0].favoritesCount" isInteger
jsonpath "$.data.articles[0].author.username" == "art_{{uid}}"

# List by tag
GET {{host}}/api/articles?tag=d_{{uid}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.articles" isList
jsonpath "$.data.articlesCount" isInteger
jsonpath "$.data.articlesCount" >= 1
jsonpath "$.data.articles[0].title" isString
jsonpath "$.data.articles[0].slug" isString
jsonpath "$.data.articles[0].description" isString
jsonpath "$.data.articles[0].body" not exists
jsonpath "$.data.articles[0].tagList" isList
jsonpath "$.data.articles[0].tagList" contains "d_{{uid}}"
jsonpath "$.data.articles[0].createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.articles[0].updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.articles[0].favorited" isBoolean
jsonpath "$.data.articles[0].favoritesCount" isInteger
jsonpath "$.data.articles[0].author.username" isString

# List articles without auth
GET {{host}}/api/articles
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.articles" isList
jsonpath "$.data.articlesCount" isInteger

# Get single article
GET {{host}}/api/articles/{{slug}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.title" == "Test Article {{uid}}"
jsonpath "$.data.article.slug" == "{{slug}}"
jsonpath "$.data.article.description" == "Test description"
jsonpath "$.data.article.body" == "Test body content"
jsonpath "$.data.article.tagList" isList
jsonpath "$.data.article.createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.favorited" == false
jsonpath "$.data.article.favoritesCount" == 0
jsonpath "$.data.article.author.username" == "art_{{uid}}"

# Update article body
PUT {{host}}/api/articles/{{slug}}
Authorization: Bearer {{token}}
{
  "article": {
    "body": "Updated body content"
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.title" == "Test Article {{uid}}"
jsonpath "$.data.article.slug" == "{{slug}}"
jsonpath "$.data.article.description" == "Test description"
jsonpath "$.data.article.body" == "Updated body content"
jsonpath "$.data.article.tagList" isList
jsonpath "$.data.article.tagList" count == 2
jsonpath "$.data.article.tagList" contains "d_{{uid}}"
jsonpath "$.data.article.tagList" contains "t_{{uid}}"
jsonpath "$.data.article.createdAt" == "{{created_at}}"
jsonpath "$.data.article.updatedAt" != "{{updated_at}}"
jsonpath "$.data.article.favorited" isBoolean
jsonpath "$.data.article.favoritesCount" isInteger
jsonpath "$.data.article.author.username" == "art_{{uid}}"

# Verify update persisted
GET {{host}}/api/articles/{{slug}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.title" == "Test Article {{uid}}"
jsonpath "$.data.article.slug" == "{{slug}}"
jsonpath "$.data.article.description" == "Test description"
jsonpath "$.data.article.body" == "Updated body content"
jsonpath "$.data.article.tagList" isList
jsonpath "$.data.article.tagList" count == 2
jsonpath "$.data.article.tagList" contains "d_{{uid}}"
jsonpath "$.data.article.tagList" contains "t_{{uid}}"
jsonpath "$.data.article.createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.favorited" isBoolean
jsonpath "$.data.article.favoritesCount" isInteger
jsonpath "$.data.article.author.username" == "art_{{uid}}"

# Update article without tagList: tags should be preserved
PUT {{host}}/api/articles/{{slug}}
Authorization: Bearer {{token}}
{
  "article": {
    "body": "Body without touching tags"
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.body" == "Body without touching tags"
jsonpath "$.data.article.tagList" count == 2
jsonpath "$.data.article.tagList" contains "d_{{uid}}"
jsonpath "$.data.article.tagList" contains "t_{{uid}}"

# Update article: remove all tags with empty array
PUT {{host}}/api/articles/{{slug}}
Authorization: Bearer {{token}}
{
  "article": {
    "tagList": []
  }
}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.tagList" isList
jsonpath "$.data.article.tagList" count == 0

# Verify tags were actually removed
GET {{host}}/api/articles/{{slug}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.tagList" isList
jsonpath "$.data.article.tagList" count == 0

# Update article: tagList null should be rejected
PUT {{host}}/api/articles/{{slug}}
Authorization: Bearer {{token}}
{
  "article": {
    "tagList": null
  }
}
HTTP 422
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists

# Delete article
DELETE {{host}}/api/articles/{{slug}}
Authorization: Bearer {{token}}
HTTP 204

# Verify deletion
GET {{host}}/api/articles/{{slug}}
HTTP 404
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.errors.article[0]" == "not found"
