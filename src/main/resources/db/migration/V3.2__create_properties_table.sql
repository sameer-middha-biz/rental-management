-- Properties: TENANT-SCOPED. Core listing entity.
-- owner_id / default_housekeeper_id reference users but are not FK-enforced
-- at the DB level; tenant alignment is validated in the service layer.
CREATE TABLE properties (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            NOT NULL,
    owner_id                    UUID,
    -- Identity
    name                        VARCHAR(200)    NOT NULL,
    slug                        VARCHAR(200)    NOT NULL,
    description                 TEXT,
    property_type               VARCHAR(50)     NOT NULL,
    -- Address
    address_line1               VARCHAR(255),
    address_line2               VARCHAR(255),
    city                        VARCHAR(100),
    state_province              VARCHAR(100),
    postal_code                 VARCHAR(20),
    country                     VARCHAR(3),
    latitude                    DECIMAL(10,7),
    longitude                   DECIMAL(11,7),
    -- Capacity
    max_guests                  INTEGER,
    bedrooms                    INTEGER,
    bathrooms                   INTEGER,
    beds                        INTEGER,
    -- Check-in/out times (TIME WITHOUT TIME ZONE; tenant timezone applied when needed)
    check_in_time               TIME,
    check_out_time              TIME,
    -- Notes / assignments
    internal_notes              TEXT,
    default_housekeeper_id      UUID,
    -- Lifecycle
    status                      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    archived_at                 TIMESTAMPTZ,
    -- Audit (matches BaseEntity)
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by                  UUID,
    updated_by                  UUID,
    version                     BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_properties_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT chk_properties_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_properties_property_type
        CHECK (property_type IN ('APARTMENT', 'VILLA', 'COTTAGE', 'HOUSE', 'STUDIO', 'OTHER'))
);

-- Unique slug per tenant (not globally unique)
CREATE UNIQUE INDEX idx_properties_tenant_slug ON properties (tenant_id, slug);
CREATE INDEX idx_properties_tenant ON properties (tenant_id);
CREATE INDEX idx_properties_tenant_status ON properties (tenant_id, status);
CREATE INDEX idx_properties_tenant_owner ON properties (tenant_id, owner_id) WHERE owner_id IS NOT NULL;
