-- Property tags: TENANT-SCOPED. Arbitrary labels (e.g., "beachfront", "luxury") applied to properties.
CREATE TABLE property_tags (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(50)     NOT NULL,
    color           VARCHAR(20),
    -- Audit (matches BaseEntity)
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_property_tags_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT uk_property_tags_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_property_tags_tenant ON property_tags (tenant_id);

-- Join: property <-> tag (many-to-many, tenant-scoped)
CREATE TABLE property_tag_assignments (
    property_id     UUID            NOT NULL,
    tag_id          UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (property_id, tag_id),
    CONSTRAINT fk_prop_tag_assignments_property
        FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT fk_prop_tag_assignments_tag
        FOREIGN KEY (tag_id) REFERENCES property_tags (id) ON DELETE CASCADE,
    CONSTRAINT fk_prop_tag_assignments_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);

CREATE INDEX idx_prop_tag_assignments_tenant ON property_tag_assignments (tenant_id);
CREATE INDEX idx_prop_tag_assignments_tag ON property_tag_assignments (tag_id);

-- Property groups: TENANT-SCOPED. Logical groupings (by location, owner, or custom).
CREATE TABLE property_groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    description     VARCHAR(500),
    group_type      VARCHAR(30)     NOT NULL,
    -- Audit (matches BaseEntity)
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_property_groups_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT uk_property_groups_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT chk_property_groups_type CHECK (group_type IN ('LOCATION', 'OWNER', 'CUSTOM'))
);

CREATE INDEX idx_property_groups_tenant ON property_groups (tenant_id);

-- Join: property <-> group (many-to-many)
CREATE TABLE property_group_members (
    property_id     UUID            NOT NULL,
    group_id        UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (property_id, group_id),
    CONSTRAINT fk_prop_group_members_property
        FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT fk_prop_group_members_group
        FOREIGN KEY (group_id) REFERENCES property_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_prop_group_members_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);

CREATE INDEX idx_prop_group_members_tenant ON property_group_members (tenant_id);
CREATE INDEX idx_prop_group_members_group ON property_group_members (group_id);
