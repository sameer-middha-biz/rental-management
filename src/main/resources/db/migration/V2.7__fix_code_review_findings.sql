-- V2.7: Fix code review findings from Phase 1-2-3 review
-- Addresses: Issue #1 (global email uniqueness), Issue #9 (invited_by FK)

-- Issue #1: Drop global email uniqueness constraint that breaks multi-tenancy.
-- The per-tenant constraint (uq_users_tenant_email) is the correct one.
ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_users_email;

-- Issue #9: Fix ON DELETE behavior for invitations.invited_by.
-- If the inviting user is deleted, set invited_by to NULL instead of blocking deletion.
ALTER TABLE invitations DROP CONSTRAINT IF EXISTS invitations_invited_by_fkey;
ALTER TABLE invitations ALTER COLUMN invited_by DROP NOT NULL;
ALTER TABLE invitations ADD CONSTRAINT invitations_invited_by_fkey
    FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE SET NULL;
