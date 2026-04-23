-- Subscriptions: TENANT-SCOPED. One active subscription per tenant.
-- A tenant is auto-assigned the STARTER plan on registration (free tier).
CREATE TABLE subscriptions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            NOT NULL,
    plan_id                     UUID            NOT NULL,
    status                      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    -- Lifecycle dates
    start_date                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    end_date                    TIMESTAMPTZ,
    trial_ends_at               TIMESTAMPTZ,
    cancelled_at                TIMESTAMPTZ,
    -- Billing (populated when Stripe is wired)
    stripe_subscription_id      VARCHAR(100),
    stripe_customer_id          VARCHAR(100),
    current_period_start        TIMESTAMPTZ,
    current_period_end          TIMESTAMPTZ,
    -- Audit
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by                  UUID,
    updated_by                  UUID,
    version                     BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_subscriptions_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_subscriptions_plan
        FOREIGN KEY (plan_id) REFERENCES subscription_plans (id),
    CONSTRAINT chk_subscriptions_status
        CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED'))
);

-- Enforce at most one ACTIVE/TRIALING subscription per tenant
CREATE UNIQUE INDEX idx_subscriptions_tenant_active
    ON subscriptions (tenant_id)
    WHERE status IN ('TRIALING', 'ACTIVE', 'PAST_DUE');

CREATE INDEX idx_subscriptions_tenant ON subscriptions (tenant_id);
CREATE INDEX idx_subscriptions_status ON subscriptions (status);
CREATE INDEX idx_subscriptions_stripe ON subscriptions (stripe_subscription_id) WHERE stripe_subscription_id IS NOT NULL;
