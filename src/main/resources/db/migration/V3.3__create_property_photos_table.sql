-- Property photos: TENANT-SCOPED. One row per uploaded image.
-- Actual image data lives in S3 (or LocalStack locally); s3_key is the pointer.
-- sort_order: integer, lower = shown first. Primary photo = sort_order 0.
CREATE TABLE property_photos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    property_id     UUID            NOT NULL,
    s3_key          VARCHAR(500)    NOT NULL,
    filename        VARCHAR(255),
    content_type    VARCHAR(100),
    size_bytes      BIGINT,
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    caption         VARCHAR(255),
    -- Audit (matches BaseEntity)
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_property_photos_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_property_photos_property
        FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT uk_property_photos_s3_key UNIQUE (s3_key)
);

CREATE INDEX idx_property_photos_property ON property_photos (property_id, sort_order);
CREATE INDEX idx_property_photos_tenant ON property_photos (tenant_id);
