-- Seasonal rates: per-property date ranges that override the base price.
-- If a night falls in multiple overlapping ranges, the one with the LATER start_date wins
-- (handled in PricingService). Ranges are inclusive on both ends.
CREATE TABLE seasonal_rates (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    property_id                     UUID            NOT NULL,
    name                            VARCHAR(100)    NOT NULL,
    start_date                      DATE            NOT NULL,
    end_date                        DATE            NOT NULL,
    price_per_night_minor_units     BIGINT          NOT NULL,
    min_stay                        INTEGER,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by                      UUID,
    updated_by                      UUID,
    version                         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_seasonal_rates_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_seasonal_rates_property
        FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT chk_seasonal_rates_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_seasonal_rates_price CHECK (price_per_night_minor_units >= 0)
);

CREATE INDEX idx_seasonal_rates_tenant ON seasonal_rates (tenant_id);
CREATE INDEX idx_seasonal_rates_property_range
    ON seasonal_rates (property_id, start_date, end_date);
