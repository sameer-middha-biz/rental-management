-- Pricing rules: priority-ordered discounts/surcharges applied after seasonal rates.
-- rule_type drives interpretation of `conditions` JSONB:
--   MIN_NIGHTS_DISCOUNT       conditions = { minNights: 7 }         -> applies if nights >= minNights
--   LAST_MINUTE_DISCOUNT      conditions = { daysBefore: 7 }        -> applies if checkIn - now <= daysBefore
--   EARLY_BIRD_DISCOUNT       conditions = { daysBefore: 60 }       -> applies if checkIn - now >= daysBefore
--   WEEKEND_SURCHARGE         conditions = { nights: ["FRI","SAT"] } -> per-night surcharge on matching weekdays
-- adjustment_type: PERCENT (discount_percent used) or FIXED (discount_amount_minor_units used).
-- Lower priority number applies first. Each rule stacks multiplicatively for PERCENT, additively for FIXED.
CREATE TABLE pricing_rules (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    property_id                     UUID,               -- NULL = applies to all properties in tenant
    name                            VARCHAR(100)    NOT NULL,
    rule_type                       VARCHAR(40)     NOT NULL,
    adjustment_type                 VARCHAR(10)     NOT NULL,
    priority                        INTEGER         NOT NULL DEFAULT 100,
    discount_percent                NUMERIC(5,2),       -- e.g. 10.00 = 10% off
    discount_amount_minor_units     BIGINT,             -- used when adjustment_type = FIXED
    conditions                      JSONB           NOT NULL DEFAULT '{}'::jsonb,
    active                          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by                      UUID,
    updated_by                      UUID,
    version                         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_pricing_rules_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_pricing_rules_property
        FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT chk_pricing_rules_adj_type
        CHECK (adjustment_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT chk_pricing_rules_values CHECK (
        (adjustment_type = 'PERCENT' AND discount_percent IS NOT NULL)
     OR (adjustment_type = 'FIXED' AND discount_amount_minor_units IS NOT NULL)
    )
);

CREATE INDEX idx_pricing_rules_tenant_property_priority
    ON pricing_rules (tenant_id, property_id, priority);
CREATE INDEX idx_pricing_rules_active ON pricing_rules (tenant_id) WHERE active = TRUE;
