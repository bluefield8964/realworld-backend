# Anonymous viewer should get locked/preview access for subscription article
GET {{host}}/api/articles/seed-subscription-article
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.slug" == "seed-subscription-article"
jsonpath "$.data.article.access.viewerState" == "UNAUTHENTICATED"
jsonpath "$.data.article.access.canReadBody" == false
jsonpath "$.data.article.access.subscriptionRequired" == true
jsonpath "$.data.article.access.bundleCode" == "CREATOR_PRO_BUNDLE"
jsonpath "$.data.article.body" not exists

# Login as author should see full content
POST {{host}}/api/users/login
{
  "user": {
    "username": "seed_author_123",
    "email": "seed_author_123@test.com",
    "password": "password123"
  }
}
HTTP 200
[Captures]
author_token: jsonpath "$.data.user.bearer"

GET {{host}}/api/articles/seed-subscription-article
Authorization: Bearer {{author_token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.slug" == "seed-subscription-article"
jsonpath "$.data.article.access.viewerState" == "FULL"
jsonpath "$.data.article.access.canReadBody" == true
jsonpath "$.data.article.body" isString
jsonpath "$.data.article.body" == "This is a seed article for subscription flow testing."

# Login as subscribed reader should also see full content
POST {{host}}/api/users/login
{
  "user": {
    "username": "seed_reader_123",
    "email": "seed_reader_123@test.com",
    "password": "password123"
  }
}
HTTP 200
[Captures]
reader_token: jsonpath "$.data.user.bearer"

GET {{host}}/api/articles/seed-subscription-article
Authorization: Bearer {{reader_token}}
HTTP 200
[Asserts]
jsonpath "$.code" isInteger
jsonpath "$.message" isString
jsonpath "$.data" exists
jsonpath "$.data.article.slug" == "seed-subscription-article"
jsonpath "$.data.article.access.viewerState" == "FULL"
jsonpath "$.data.article.access.canReadBody" == true
jsonpath "$.data.article.body" isString
jsonpath "$.data.article.body" == "This is a seed article for subscription flow testing."
