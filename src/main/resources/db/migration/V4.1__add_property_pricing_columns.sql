-- Property pricing: base per-night rate in minor units (e.g. pence) and ISO currency code.
-- Minor units are used everywhere to avoid floating-point and match Stripe's money representation.
ALTER TABLE properties
    ADD COLUMN base_price_per_night_minor_units BIGINT,
    ADD COLUMN currency VARCHAR(3);

-- Existing rows default to tenant's currency and a placeholder base price of 0.
-- Tenants must edit each property's price before it is bookable; the pricing service
-- rejects bookings with base_price_per_night_minor_units IS NULL.
UPDATE properties p
SET currency = COALESCE(p.currency, t.default_currency),
    base_price_per_night_minor_units = COALESCE(p.base_price_per_night_minor_units, 0)
FROM tenants t
WHERE p.tenant_id = t.id;
