-- Add PROPERTY_DELETE permission. V2.6 seeded only CREATE/VIEW/EDIT;
-- Phase 4 DELETE endpoint requires its own permission.
--
-- Note: V2.6 used INSERT ... SELECT FROM permissions for SUPER_ADMIN and
-- AGENCY_ADMIN. Those inserts ran before this migration and did NOT pick up
-- PROPERTY_DELETE, so we must explicitly grant it to those roles here.
INSERT INTO permissions (id, code, description, module) VALUES
    ('10000000-0000-0000-0000-000000000050', 'PROPERTY_DELETE', 'Delete (archive) properties', 'property');

INSERT INTO role_permissions (role_id, permission_id) VALUES
    -- SUPER_ADMIN
    ('00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000050'),
    -- AGENCY_ADMIN
    ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000050'),
    -- PROPERTY_MANAGER
    ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000050');
