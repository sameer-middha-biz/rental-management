-- Amenities: GLOBAL reference table (not tenant-scoped). Standard vacation rental amenities.
-- Tenants pick from this catalog via property_amenities join. Custom/tenant-specific
-- amenities go into property_custom_amenities (free-text, per-property).
CREATE TABLE amenities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)     NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    category        VARCHAR(50)     NOT NULL,
    icon            VARCHAR(50),
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uk_amenities_code UNIQUE (code)
);

CREATE INDEX idx_amenities_category ON amenities (category, sort_order);

-- Join: property <-> amenity (many-to-many)
CREATE TABLE property_amenities (
    property_id     UUID            NOT NULL,
    amenity_id      UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (property_id, amenity_id),
    CONSTRAINT fk_prop_amenities_property
        FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT fk_prop_amenities_amenity
        FOREIGN KEY (amenity_id) REFERENCES amenities (id) ON DELETE CASCADE,
    CONSTRAINT fk_prop_amenities_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);

CREATE INDEX idx_property_amenities_tenant ON property_amenities (tenant_id);
CREATE INDEX idx_property_amenities_amenity ON property_amenities (amenity_id);

-- Free-text amenities specific to a property (things not in the global catalog)
CREATE TABLE property_custom_amenities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id     UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT fk_prop_custom_amenities_property
        FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT fk_prop_custom_amenities_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);

CREATE INDEX idx_property_custom_amenities_property ON property_custom_amenities (property_id);
CREATE INDEX idx_property_custom_amenities_tenant ON property_custom_amenities (tenant_id);

-- Seed common vacation rental amenities. Codes are stable identifiers (never rename).
INSERT INTO amenities (code, name, category, icon, sort_order) VALUES
    ('WIFI',              'WiFi',                'connectivity', 'wifi',       10),
    ('AIR_CONDITIONING',  'Air Conditioning',    'climate',      'ac',         20),
    ('HEATING',           'Heating',             'climate',      'heat',       21),
    ('KITCHEN',           'Kitchen',             'kitchen',      'kitchen',    30),
    ('WASHER',            'Washing Machine',     'laundry',      'washer',     40),
    ('DRYER',             'Dryer',               'laundry',      'dryer',      41),
    ('FREE_PARKING',      'Free Parking',        'parking',      'parking',    50),
    ('POOL',              'Pool',                'outdoor',      'pool',       60),
    ('HOT_TUB',           'Hot Tub',             'outdoor',      'hottub',     61),
    ('BBQ',               'BBQ Grill',           'outdoor',      'bbq',        62),
    ('TV',                'TV',                  'entertainment','tv',         70),
    ('NETFLIX',           'Netflix',             'entertainment','netflix',    71),
    ('WORKSPACE',         'Dedicated Workspace', 'work',         'desk',       80),
    ('GYM',               'Gym',                 'wellness',     'gym',        90),
    ('PET_FRIENDLY',      'Pet Friendly',        'policies',     'pet',       100),
    ('SMOKE_ALARM',       'Smoke Alarm',         'safety',       'smoke',     110),
    ('CARBON_MONOXIDE_ALARM','Carbon Monoxide Alarm','safety',   'co',        111),
    ('FIRST_AID_KIT',     'First Aid Kit',       'safety',       'firstaid',  112),
    ('ELEVATOR',          'Elevator',            'accessibility','elevator',  120),
    ('WHEELCHAIR_ACCESSIBLE','Wheelchair Accessible','accessibility','wheelchair',121);
