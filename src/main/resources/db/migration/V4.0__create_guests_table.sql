-- Guests: TENANT-SCOPED. CRM record for booking guests.
-- PII fields (email, phone, notes) are encrypted at rest via EncryptedStringConverter (AES-256-GCM with random IV).
-- Because ciphertext is non-deterministic, lookups / uniqueness checks cannot be done on the
-- encrypted column. We store a SHA-256 hash of lower(email) alongside for the lookup path.
CREATE TABLE guests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    first_name          VARCHAR(100)    NOT NULL,
    last_name           VARCHAR(100)    NOT NULL,
    -- Encrypted PII (TEXT because ciphertext + IV + tag exceeds plaintext length)
    email               TEXT,
    email_hash          VARCHAR(64),    -- SHA-256 hex of lower(email) + tenantId (see GuestService)
    phone               TEXT,
    notes               TEXT,
    -- Non-PII metadata
    nationality         VARCHAR(3),     -- ISO 3166-1 alpha-3
    id_document_s3_key  VARCHAR(500),   -- optional passport/ID scan stored in S3
    total_bookings      INTEGER         NOT NULL DEFAULT 0,
    last_stay_at        TIMESTAMPTZ,
    -- GDPR erasure marker. Once TRUE, PII fields have been anonymised.
    gdpr_erased         BOOLEAN         NOT NULL DEFAULT FALSE,
    gdpr_erased_at      TIMESTAMPTZ,
    -- Audit (matches BaseEntity)
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_guests_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);

-- Unique email per tenant (via hash — ciphertext itself is non-deterministic).
-- Partial: once a guest is GDPR-erased the hash is cleared, so it should not block re-entry of that email.
CREATE UNIQUE INDEX idx_guests_tenant_email_hash ON guests (tenant_id, email_hash)
    WHERE email_hash IS NOT NULL;

CREATE INDEX idx_guests_tenant ON guests (tenant_id);
CREATE INDEX idx_guests_tenant_name ON guests (tenant_id, last_name, first_name);
