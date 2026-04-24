-- Bookings: tenant-scoped reservations.
-- Availability is computed by querying this table for rows with an overlapping
-- [check_in, check_out) range where status NOT IN ('CANCELLED', 'DECLINED').
-- Concurrency is protected via pg_advisory_xact_lock keyed on property_id (see AvailabilityService).
CREATE TABLE bookings (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    property_id                     UUID            NOT NULL,
    guest_id                        UUID            NOT NULL,
    booking_reference               VARCHAR(30)     NOT NULL,     -- human-readable code e.g. "BK-8F3A21"
    check_in                        DATE            NOT NULL,
    check_out                       DATE            NOT NULL,
    status                          VARCHAR(20)     NOT NULL,
    guest_count                     INTEGER         NOT NULL DEFAULT 1,
    total_price_minor_units         BIGINT          NOT NULL,
    currency                        VARCHAR(3)      NOT NULL,
    source                          VARCHAR(30)     NOT NULL DEFAULT 'DIRECT',
    coupon_code                     VARCHAR(50),
    special_requests                TEXT,
    notes                           TEXT,
    cancelled_at                    TIMESTAMPTZ,
    cancelled_reason                TEXT,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by                      UUID,
    updated_by                      UUID,
    version                         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_bookings_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_bookings_property
        FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_guest
        FOREIGN KEY (guest_id) REFERENCES guests (id) ON DELETE RESTRICT,
    CONSTRAINT chk_bookings_dates CHECK (check_out > check_in),
    CONSTRAINT chk_bookings_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'DECLINED')),
    CONSTRAINT uq_bookings_tenant_reference UNIQUE (tenant_id, booking_reference)
);

CREATE INDEX idx_bookings_tenant ON bookings (tenant_id);
CREATE INDEX idx_bookings_property_dates
    ON bookings (property_id, check_in, check_out);
CREATE INDEX idx_bookings_guest ON bookings (guest_id);
