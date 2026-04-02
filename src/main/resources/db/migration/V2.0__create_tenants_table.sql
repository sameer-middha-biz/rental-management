-- Tenants table: NOT tenant-scoped (this IS the tenant).
-- Each row represents one agency/account on the platform.
CREATE TABLE tenants (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                        VARCHAR(200)    NOT NULL,
    slug                        VARCHAR(100)    NOT NULL,
    custom_domain               VARCHAR(255),
    timezone                    VARCHAR(50)     NOT NULL DEFAULT 'UTC',
    default_currency            VARCHAR(3)      NOT NULL DEFAULT 'GBP',
    management_fee_type         VARCHAR(20)     NOT NULL DEFAULT 'PERCENTAGE',
    management_fee_percentage   DECIMAL(5,2),
    management_fee_fixed        BIGINT,
    logo_s3_key                 VARCHAR(500),
    contact_email               VARCHAR(255)    NOT NULL,
    contact_phone               VARCHAR(30),
    address                     TEXT,
    status                      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_tenants_slug ON tenants (slug);
CREATE INDEX idx_tenants_custom_domain ON tenants (custom_domain) WHERE custom_domain IS NOT NULL;
CREATE INDEX idx_tenants_status ON tenants (status);
