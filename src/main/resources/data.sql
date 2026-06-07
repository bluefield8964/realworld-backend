/* ============================================================================
   Local test seed data

   Goal:
   - One login-ready author/account
   - One public article
   - One bundle-backed subscription plan
   - One one-time product for order checkout
   - One active entitlement grant so protected article/commerce flows are usable

   This file is idempotent enough for local dev with ddl-auto=update and
   spring.sql.init.mode=always.
   ============================================================================ */

/* --------------------------------------------------------------------------
   Auth + article author seed
   -------------------------------------------------------------------------- */
INSERT INTO user_auth_profiles (username, email, password_hash, status, mfa_enabled)
SELECT 'seed_author_123', 'seed_author_123@test.com', '{bcrypt}$2a$10$x4GG66vtpi8paidILYzNGuBsAimWMb7hGS59jWaQdsqX2N7xHat/G', 'ACTIVE', b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM user_auth_profiles WHERE username = 'seed_author_123'
);

INSERT INTO user_auth_profiles (username, email, password_hash, status, mfa_enabled)
SELECT 'seed_reader_123', 'seed_reader_123@test.com', '{bcrypt}$2a$10$x4GG66vtpi8paidILYzNGuBsAimWMb7hGS59jWaQdsqX2N7xHat/G', 'ACTIVE', b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM user_auth_profiles WHERE username = 'seed_reader_123'
);

INSERT INTO user_auth_profiles (username, email, password_hash, status, mfa_enabled)
SELECT 'seed_author_456', 'seed_author_456@test.com', '{bcrypt}$2a$10$x4GG66vtpi8paidILYzNGuBsAimWMb7hGS59jWaQdsqX2N7xHat/G', 'ACTIVE', b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM user_auth_profiles WHERE username = 'seed_author_456'
);

INSERT INTO users (user_auth_id, username, email, bio, image)
SELECT uap.id, uap.username, uap.email, 'Seed author for local testing', NULL
FROM user_auth_profiles uap
WHERE uap.username = 'seed_author_123'
  AND NOT EXISTS (
    SELECT 1 FROM users u WHERE u.user_auth_id = uap.id
  );

INSERT INTO users (user_auth_id, username, email, bio, image)
SELECT uap.id, uap.username, uap.email, 'Seed reader for local testing', NULL
FROM user_auth_profiles uap
WHERE uap.username = 'seed_reader_123'
  AND NOT EXISTS (
    SELECT 1 FROM users u WHERE u.user_auth_id = uap.id
  );

INSERT INTO users (user_auth_id, username, email, bio, image)
SELECT uap.id, uap.username, uap.email, 'Second seed author for local testing', NULL
FROM user_auth_profiles uap
WHERE uap.username = 'seed_author_456'
  AND NOT EXISTS (
    SELECT 1 FROM users u WHERE u.user_auth_id = uap.id
  );

INSERT INTO authors (user_id, username)
SELECT u.id, u.username
FROM users u
WHERE u.username = 'seed_author_123'
  AND NOT EXISTS (
    SELECT 1 FROM authors a WHERE a.user_id = u.id
  );

INSERT INTO authors (user_id, username)
SELECT u.id, u.username
FROM users u
WHERE u.username = 'seed_author_456'
  AND NOT EXISTS (
    SELECT 1 FROM authors a WHERE a.user_id = u.id
  );

INSERT INTO roles (name, description)
SELECT 'USER', 'Default user role'
WHERE NOT EXISTS (
  SELECT 1 FROM roles WHERE name = 'USER'
);

INSERT INTO roles (name, description)
SELECT 'AUTHOR', 'Article author role'
WHERE NOT EXISTS (
  SELECT 1 FROM roles WHERE name = 'AUTHOR'
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'AUTHOR'
WHERE u.username = 'seed_author_123'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

/* --------------------------------------------------------------------------
   Article tags + demo article
   -------------------------------------------------------------------------- */
INSERT INTO tags (name)
SELECT 'demo'
WHERE NOT EXISTS (SELECT 1 FROM tags WHERE name = 'demo');

INSERT INTO tags (name)
SELECT 'commerce'
WHERE NOT EXISTS (SELECT 1 FROM tags WHERE name = 'commerce');

INSERT INTO articles (author_id, body, created_at, description, favorites_count, slug, title, updated_at)
SELECT a.id,
       'This is a seed article for subscription flow testing.',
       UTC_TIMESTAMP(),
       'Seed article for local testing',
       0,
       'seed-subscription-article',
       'Seed Subscription Article',
       UTC_TIMESTAMP()
FROM authors a
WHERE a.username = 'seed_author_123'
  AND NOT EXISTS (
    SELECT 1 FROM articles ar WHERE ar.slug = 'seed-subscription-article'
  );

INSERT INTO article_tags (article_id, tag_id)
SELECT ar.id, t.id
FROM articles ar
JOIN tags t ON t.name = 'demo'
WHERE ar.slug = 'seed-subscription-article'
  AND NOT EXISTS (
    SELECT 1 FROM article_tags at WHERE at.article_id = ar.id AND at.tag_id = t.id
  );

INSERT INTO article_tags (article_id, tag_id)
SELECT ar.id, t.id
FROM articles ar
JOIN tags t ON t.name = 'commerce'
WHERE ar.slug = 'seed-subscription-article'
  AND NOT EXISTS (
    SELECT 1 FROM article_tags at WHERE at.article_id = ar.id AND at.tag_id = t.id
  );

INSERT INTO articles (author_id, body, created_at, description, favorites_count, slug, title, updated_at)
SELECT a.id,
       'A second article seeded for feed, favorites, and follow tests.',
       UTC_TIMESTAMP(),
       'Seed article for home feed testing',
       0,
       'seed-home-feed-article',
       'Seed Home Feed Article',
       UTC_TIMESTAMP()
FROM authors a
WHERE a.username = 'seed_author_456'
  AND NOT EXISTS (
    SELECT 1 FROM articles ar WHERE ar.slug = 'seed-home-feed-article'
  );

INSERT INTO article_tags (article_id, tag_id)
SELECT ar.id, t.id
FROM articles ar
JOIN tags t ON t.name = 'demo'
WHERE ar.slug = 'seed-home-feed-article'
  AND NOT EXISTS (
    SELECT 1 FROM article_tags at WHERE at.article_id = ar.id AND at.tag_id = t.id
  );

INSERT INTO article_tags (article_id, tag_id)
SELECT ar.id, t.id
FROM articles ar
JOIN tags t ON t.name = 'commerce'
WHERE ar.slug = 'seed-home-feed-article'
  AND NOT EXISTS (
    SELECT 1 FROM article_tags at WHERE at.article_id = ar.id AND at.tag_id = t.id
  );

INSERT INTO user_follow (follower_id, following_id)
SELECT follower.id, following.id
FROM users follower
JOIN users following ON following.username = 'seed_author_123'
WHERE follower.username = 'seed_reader_123'
  AND NOT EXISTS (
    SELECT 1 FROM user_follow uf
    WHERE uf.follower_id = follower.id
      AND uf.following_id = following.id
  );

INSERT INTO favorites (user_id, article_id, created_at)
SELECT u.id, ar.id, UTC_TIMESTAMP()
FROM users u
JOIN articles ar ON ar.slug = 'seed-subscription-article'
WHERE u.username = 'seed_reader_123'
  AND NOT EXISTS (
    SELECT 1 FROM favorites f WHERE f.user_id = u.id AND f.article_id = ar.id
  );

INSERT INTO favorites (user_id, article_id, created_at)
SELECT u.id, ar.id, UTC_TIMESTAMP()
FROM users u
JOIN articles ar ON ar.slug = 'seed-home-feed-article'
WHERE u.username = 'seed_author_456'
  AND NOT EXISTS (
    SELECT 1 FROM favorites f WHERE f.user_id = u.id AND f.article_id = ar.id
  );

INSERT INTO `comment` (body, user_profile_id, article_id, created_at, updated_at)
SELECT
  'Great seed article, this is useful for local testing.',
  u.id,
  ar.id,
  UTC_TIMESTAMP(),
  UTC_TIMESTAMP()
FROM users u
JOIN articles ar ON ar.slug = 'seed-subscription-article'
WHERE u.username = 'seed_reader_123'
  AND NOT EXISTS (
    SELECT 1 FROM `comment` c
    WHERE c.body = 'Great seed article, this is useful for local testing.'
      AND c.article_id = ar.id
      AND c.user_profile_id = u.id
  );

INSERT INTO `comment` (body, user_profile_id, article_id, created_at, updated_at)
SELECT
  'Follow-up note for comment and discussion flow.',
  u.id,
  ar.id,
  UTC_TIMESTAMP(),
  UTC_TIMESTAMP()
FROM users u
JOIN articles ar ON ar.slug = 'seed-home-feed-article'
WHERE u.username = 'seed_author_123'
  AND NOT EXISTS (
    SELECT 1 FROM `comment` c
    WHERE c.body = 'Follow-up note for comment and discussion flow.'
      AND c.article_id = ar.id
      AND c.user_profile_id = u.id
  );

/* --------------------------------------------------------------------------
   Subscription catalog
   -------------------------------------------------------------------------- */
INSERT INTO feature_bundles (bundle_code, name, description, status, created_at, updated_at)
SELECT
  'CREATOR_PRO_BUNDLE',
  'Creator Pro Bundle',
  'Local bundle used to gate creator article access.',
  'ACTIVE',
  UTC_TIMESTAMP(),
  UTC_TIMESTAMP()
WHERE NOT EXISTS (
  SELECT 1 FROM feature_bundles WHERE bundle_code = 'CREATOR_PRO_BUNDLE'
);

INSERT INTO feature_bundle_items (bundle_id, feature_code)
SELECT fb.id, 'CREATOR_POST_ACCESS'
FROM feature_bundles fb
WHERE fb.bundle_code = 'CREATOR_PRO_BUNDLE'
  AND NOT EXISTS (
    SELECT 1 FROM feature_bundle_items fbi WHERE fbi.bundle_id = fb.id AND fbi.feature_code = 'CREATOR_POST_ACCESS'
  );

INSERT INTO feature_bundle_items (bundle_id, feature_code)
SELECT fb.id, 'CREATOR_COMMENT_ACCESS'
FROM feature_bundles fb
WHERE fb.bundle_code = 'CREATOR_PRO_BUNDLE'
  AND NOT EXISTS (
    SELECT 1 FROM feature_bundle_items fbi WHERE fbi.bundle_id = fb.id AND fbi.feature_code = 'CREATOR_COMMENT_ACCESS'
  );

INSERT INTO subscription_plans (
  plan_code,
  name,
  duration,
  description,
  status,
  price,
  currency,
  billing_interval,
  created_at,
  updated_at,
  feature_bundle_id
)
SELECT
  'CREATOR_PRO_MONTHLY',
  'Creator Pro Monthly',
  2592000,
  'Monthly subscription plan used for local development.',
  'ACTIVE',
  99.00,
  'usd',
  'MONTHLY',
  UTC_TIMESTAMP(),
  UTC_TIMESTAMP(),
  fb.id
FROM feature_bundles fb
WHERE fb.bundle_code = 'CREATOR_PRO_BUNDLE'
  AND NOT EXISTS (
    SELECT 1 FROM subscription_plans sp WHERE sp.plan_code = 'CREATOR_PRO_MONTHLY'
  );

INSERT INTO plan_provider_mappings (
  plan_id,
  provider,
  provider_price_id,
  provider_product_id,
  active,
  created_at,
  updated_at
)
SELECT
  sp.id,
  'STRIPE',
  'price_1TdNQoFBxenD80aO15hGdV3j',
  'prod_Uccd56iz2xssPs',
  b'1',
  UTC_TIMESTAMP(),
  UTC_TIMESTAMP()
FROM subscription_plans sp
WHERE sp.plan_code = 'CREATOR_PRO_MONTHLY'
  AND NOT EXISTS (
    SELECT 1
    FROM plan_provider_mappings ppm
    WHERE ppm.plan_id = sp.id
      AND ppm.provider = 'STRIPE'
  );

INSERT INTO subscription_plans (
  plan_code,
  name,
  duration,
  description,
  status,
  price,
  currency,
  billing_interval,
  created_at,
  updated_at,
  feature_bundle_id
)
SELECT
  'CREATOR_URLT_MONTHLY',
  'Creator URLT Monthly',
  2592000,
  'Monthly subscription plan used for local URLT testing.',
  'ACTIVE',
  99.00,
  'usd',
  'MONTHLY',
  UTC_TIMESTAMP(),
  UTC_TIMESTAMP(),
  fb.id
FROM feature_bundles fb
WHERE fb.bundle_code = 'CREATOR_PRO_BUNDLE'
  AND NOT EXISTS (
    SELECT 1 FROM subscription_plans sp WHERE sp.plan_code = 'CREATOR_URLT_MONTHLY'
  );

INSERT INTO plan_provider_mappings (
  plan_id,
  provider,
  provider_price_id,
  provider_product_id,
  active,
  created_at,
  updated_at
)
SELECT
  sp.id,
  'STRIPE',
  'price_1TdNQoFBxenD80aO15hGdV3j',
  'prod_Uccd56iz2xssPs',
  b'1',
  UTC_TIMESTAMP(),
  UTC_TIMESTAMP()
FROM subscription_plans sp
WHERE sp.plan_code = 'CREATOR_URLT_MONTHLY'
  AND NOT EXISTS (
    SELECT 1
    FROM plan_provider_mappings ppm
    WHERE ppm.plan_id = sp.id
      AND ppm.provider = 'STRIPE'
  );

/* --------------------------------------------------------------------------
   One-time product for order checkout
   -------------------------------------------------------------------------- */
INSERT INTO products (
  sku,
  name,
  description,
  price_amount,
  currency,
  active,
  sort_order,
  stripe_price_id
)
SELECT
  'VIP_MONTHLY',
  'VIP Monthly Access',
  'One-time checkout product used for local development.',
  9900,
  'usd',
  b'1',
  1,
  'price_1TdNOlFBxenD80aOIbvcc4Rt'
WHERE NOT EXISTS (
  SELECT 1 FROM products WHERE sku = 'VIP_MONTHLY'
);

/* --------------------------------------------------------------------------
   Seed subscription + entitlement so protected flows can be tested immediately
   -------------------------------------------------------------------------- */
INSERT INTO customer_subscriptions (
  subscription_no,
  user_id,
  plan_id,
  provider,
  subscription_url,
  status,
  provider_subscription_id,
  provider_customer_id,
  current_period_start,
  active_key,
  current_period_end,
  cancel_at_period_end,
  canceled_at,
  last_checkout_event_created_at,
  last_lifecycle_event_created_at,
  created_at,
  updated_at
)
SELECT
  'seed-sub-0001',
  u.id,
  sp.id,
  'STRIPE',
  NULL,
  'ACTIVE',
  'sub_seed_0001',
  'cus_seed_0001',
  UTC_TIMESTAMP(),
  CONCAT(u.id, ':', sp.plan_code),
  DATE_ADD(UTC_TIMESTAMP(), INTERVAL 30 DAY),
  b'0',
  NULL,
  NULL,
  NULL,
  UTC_TIMESTAMP(),
  UTC_TIMESTAMP()
FROM users u
JOIN subscription_plans sp ON sp.plan_code = 'CREATOR_PRO_MONTHLY'
WHERE u.username = 'seed_reader_123'
  AND NOT EXISTS (
    SELECT 1 FROM customer_subscriptions cs WHERE cs.subscription_no = 'seed-sub-0001'
  );

INSERT INTO entitlement_grants (
  user_id,
  resource_type,
  resource_id,
  source_type,
  source_id,
  status,
  effective_at,
  expire_at,
  version,
  created_at,
  updated_at
)
SELECT
  u.id,
  'BUNDLE',
  'CREATOR_PRO_BUNDLE',
  'SUBSCRIPTION',
  'seed-sub-0001',
  'ACTIVE',
  UTC_TIMESTAMP(),
  DATE_ADD(UTC_TIMESTAMP(), INTERVAL 30 DAY),
  1,
  UTC_TIMESTAMP(),
  UTC_TIMESTAMP()
FROM users u
WHERE u.username = 'seed_reader_123'
  AND NOT EXISTS (
    SELECT 1 FROM entitlement_grants eg
    WHERE eg.user_id = u.id
      AND eg.resource_type = 'BUNDLE'
      AND eg.resource_id = 'CREATOR_PRO_BUNDLE'
      AND eg.source_id = 'seed-sub-0001'
  );
