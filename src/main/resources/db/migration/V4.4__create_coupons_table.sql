-- Coupons: tenant-scoped discount codes.
-- discount_type = PERCENTAGE  -> discount_value is a percent (e.g. 15.00)
-- discount_type = FIXED       -> discount_value is an amount in minor units (stored in discount_value_minor_units)
-- current_uses is incremented when a coupon is applied during booking creation.
-- Coupon is valid iff: active AND now BETWEEN valid_from AND valid_until AND current_uses < max_uses (if set).
CREATE TABLE coupons (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    code                            VARCHAR(50)     NOT NULL,
    discount_type                   VARCHAR(20)     NOT NULL,
    discount_percent                NUMERIC(5,2),
    discount_value_minor_units      BIGINT,
    valid_from                      TIMESTAMPTZ,
    valid_until                     TIMESTAMPTZ,
    max_uses                        INTEGER,
    current_uses                    INTEGER         NOT NULL DEFAULT 0,
    active                          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by                      UUID,
    updated_by                      UUID,
    version                         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_coupons_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT chk_coupons_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED')),
    CONSTRAINT chk_coupons_value CHECK (
        (discount_type = 'PERCENTAGE' AND discount_percent IS NOT NULL)
     OR (discount_type = 'FIXED' AND discount_value_minor_units IS NOT NULL)
    ),
    CONSTRAINT chk_coupons_uses CHECK (current_uses >= 0)
);

-- Code is unique per tenant (case-insensitive: store uppercase, enforce via application + index).
CREATE UNIQUE INDEX idx_coupons_tenant_code ON coupons (tenant_id, code);
