# Setup: Register
POST {{host}}/api/users
{
  "user": {
    "username": "fav_{{uid}}",
    "email": "fav_{{uid}}@test.com",
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
    "title": "Favorite Article {{uid}}",
    "description": "For favorites",
    "body": "Article body"
  }
}
HTTP 201
[Captures]
slug: jsonpath "$.data.article.slug"

# Favorite article
POST {{host}}/api/articles/{{slug}}/favorite
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.title" isString
jsonpath "$.data.article.slug" isString
jsonpath "$.data.article.description" isString
jsonpath "$.data.article.body" isString
jsonpath "$.data.article.tagList" isList
jsonpath "$.data.article.createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.favorited" == true
jsonpath "$.data.article.favoritesCount" == 1
jsonpath "$.data.article.author.username" == "fav_{{uid}}"

# Verify favorite persists
GET {{host}}/api/articles/{{slug}}
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.title" isString
jsonpath "$.data.article.slug" isString
jsonpath "$.data.article.description" isString
jsonpath "$.data.article.body" isString
jsonpath "$.data.article.tagList" isList
jsonpath "$.data.article.createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.favorited" == true
jsonpath "$.data.article.favoritesCount" == 1
jsonpath "$.data.article.author.username" == "fav_{{uid}}"

# Articles filtered by favorited username
GET {{host}}/api/articles?favorited=fav_{{uid}}
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
jsonpath "$.data.articles[0].favoritesCount" >= 1

# Articles filtered by favorited username with auth
GET {{host}}/api/articles?favorited=fav_{{uid}}
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
jsonpath "$.data.articles[0].favoritesCount" >= 1

# Unfavorite article
DELETE {{host}}/api/articles/{{slug}}/favorite
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.title" isString
jsonpath "$.data.article.slug" isString
jsonpath "$.data.article.description" isString
jsonpath "$.data.article.body" isString
jsonpath "$.data.article.tagList" isList
jsonpath "$.data.article.createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.favorited" == false
jsonpath "$.data.article.favoritesCount" == 0
jsonpath "$.data.article.author.username" == "fav_{{uid}}"

# Verify unfavorite persists
GET {{host}}/api/articles/{{slug}}
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.title" isString
jsonpath "$.data.article.slug" isString
jsonpath "$.data.article.description" isString
jsonpath "$.data.article.body" isString
jsonpath "$.data.article.tagList" isList
jsonpath "$.data.article.createdAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.updatedAt" matches "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
jsonpath "$.data.article.favorited" == false
jsonpath "$.data.article.favoritesCount" == 0
jsonpath "$.data.article.author.username" == "fav_{{uid}}"

# Cleanup
DELETE {{host}}/api/articles/{{slug}}
Authorization: Bearer {{token}}
HTTP 204

