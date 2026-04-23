-- Subscription plans: GLOBAL (not tenant-scoped). Reference data.
-- Seeded in V3.6. Used by PlanEnforcementService to check tenant limits.
CREATE TABLE subscription_plans (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                        VARCHAR(50)     NOT NULL,
    code                        VARCHAR(20)     NOT NULL,
    description                 TEXT,
    -- Limits: NULL = unlimited
    max_properties              INTEGER,
    max_users                   INTEGER,
    -- Pricing in minor units (pence/cents) to avoid floating point
    monthly_price_minor_units   BIGINT          NOT NULL DEFAULT 0,
    currency                    VARCHAR(3)      NOT NULL DEFAULT 'GBP',
    -- Feature flags as JSONB for flexibility
    features                    JSONB           NOT NULL DEFAULT '{}'::jsonb,
    -- Stripe integration (populated later; not required for Phase 4)
    stripe_price_id             VARCHAR(100),
    stripe_product_id           VARCHAR(100),
    is_active                   BOOLEAN         NOT NULL DEFAULT TRUE,
    sort_order                  INTEGER         NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_subscription_plans_code ON subscription_plans (code);
CREATE INDEX idx_subscription_plans_active ON subscription_plans (is_active, sort_order);
