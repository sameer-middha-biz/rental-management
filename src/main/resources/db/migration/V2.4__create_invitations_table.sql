-- Invitations: tenant-scoped, follows BaseEntity pattern.
CREATE TABLE invitations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID            NOT NULL REFERENCES tenants(id),
    email       VARCHAR(255)    NOT NULL,
    role_id     UUID            NOT NULL REFERENCES roles(id),
    token       VARCHAR(255)    NOT NULL,
    invited_by  UUID            NOT NULL REFERENCES users(id),
    status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    expires_at  TIMESTAMPTZ     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_invitations_token ON invitations (token);
CREATE INDEX idx_invitations_tenant_id ON invitations (tenant_id);
CREATE INDEX idx_invitations_tenant_email_status ON invitations (tenant_id, email, status);
