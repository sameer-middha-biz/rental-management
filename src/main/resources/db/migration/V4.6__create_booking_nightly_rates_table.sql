-- Per-night rate breakdown for a booking. Rows are written alongside the booking in the same
-- transaction so the audit/accounting trail always matches the total on the parent booking.
CREATE TABLE booking_nightly_rates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    booking_id          UUID            NOT NULL,
    date                DATE            NOT NULL,
    rate_minor_units    BIGINT          NOT NULL,
    rate_name           VARCHAR(100)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_bnr_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_bnr_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT uq_bnr_booking_date UNIQUE (booking_id, date)
);

CREATE INDEX idx_bnr_booking ON booking_nightly_rates (booking_id);
