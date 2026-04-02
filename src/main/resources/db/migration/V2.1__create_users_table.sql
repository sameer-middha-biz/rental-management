-- Users table: tenant-scoped, follows BaseEntity pattern.
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    email               VARCHAR(255)    NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    first_name          VARCHAR(100)    NOT NULL,
    last_name           VARCHAR(100)    NOT NULL,
    phone               VARCHAR(30),
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    two_factor_enabled  BOOLEAN         NOT NULL DEFAULT FALSE,
    two_factor_secret   VARCHAR(255),
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT          NOT NULL DEFAULT 0
);

-- Per-tenant uniqueness (ARCHITECTURE.md requirement)
ALTER TABLE users ADD CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email);

-- Global uniqueness for MVP login simplification (no tenantId at login)
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);

CREATE INDEX idx_users_tenant_id ON users (tenant_id);
CREATE INDEX idx_users_tenant_status ON users (tenant_id, status);
