-- ============================================================
-- Seed system roles
-- ============================================================
INSERT INTO roles (id, name, description, is_system) VALUES
    ('00000000-0000-0000-0000-000000000001', 'SUPER_ADMIN',       'Platform-wide superadmin with cross-tenant access',        TRUE),
    ('00000000-0000-0000-0000-000000000002', 'AGENCY_ADMIN',      'Tenant administrator with all tenant-level permissions',   TRUE),
    ('00000000-0000-0000-0000-000000000003', 'PROPERTY_MANAGER',  'Manages assigned properties, bookings, guests, and tasks', TRUE),
    ('00000000-0000-0000-0000-000000000004', 'PROPERTY_OWNER',    'Read-only access to own properties and statements',        TRUE),
    ('00000000-0000-0000-0000-000000000005', 'HOUSEKEEPER',       'View and update assigned cleaning tasks only',             TRUE),
    ('00000000-0000-0000-0000-000000000006', 'GUEST',             'View own booking details and pre-arrival info',            TRUE);

-- ============================================================
-- Seed permissions (grouped by module)
-- ============================================================
INSERT INTO permissions (id, code, description, module) VALUES
    -- Tenant
    ('10000000-0000-0000-0000-000000000001', 'TENANT_VIEW',          'View tenant settings',                  'tenant'),
    ('10000000-0000-0000-0000-000000000002', 'TENANT_MANAGE',        'Manage tenant settings',                'tenant'),
    -- User
    ('10000000-0000-0000-0000-000000000003', 'USER_VIEW',            'View users in tenant',                  'user'),
    ('10000000-0000-0000-0000-000000000004', 'USER_EDIT',            'Edit user profiles',                    'user'),
    ('10000000-0000-0000-0000-000000000005', 'USER_MANAGE',          'Manage user status and roles',          'user'),
    ('10000000-0000-0000-0000-000000000006', 'USER_INVITE',          'Invite new team members',               'user'),
    -- Property
    ('10000000-0000-0000-0000-000000000007', 'PROPERTY_CREATE',      'Create properties',                     'property'),
    ('10000000-0000-0000-0000-000000000008', 'PROPERTY_VIEW',        'View properties',                       'property'),
    ('10000000-0000-0000-0000-000000000009', 'PROPERTY_EDIT',        'Edit properties',                       'property'),
    -- Booking
    ('10000000-0000-0000-0000-000000000010', 'BOOKING_CREATE',       'Create bookings',                       'booking'),
    ('10000000-0000-0000-0000-000000000011', 'BOOKING_VIEW',         'View bookings',                         'booking'),
    ('10000000-0000-0000-0000-000000000012', 'BOOKING_EDIT',         'Edit bookings',                         'booking'),
    ('10000000-0000-0000-0000-000000000013', 'BOOKING_CANCEL',       'Cancel bookings',                       'booking'),
    -- Rates & Coupons
    ('10000000-0000-0000-0000-000000000014', 'RATE_MANAGE',          'Manage seasonal rates and pricing',     'booking'),
    ('10000000-0000-0000-0000-000000000015', 'COUPON_MANAGE',        'Manage coupon codes',                   'booking'),
    -- Guest
    ('10000000-0000-0000-0000-000000000016', 'GUEST_CREATE',         'Create guest profiles',                 'guest'),
    ('10000000-0000-0000-0000-000000000017', 'GUEST_VIEW',           'View guest profiles',                   'guest'),
    ('10000000-0000-0000-0000-000000000018', 'GUEST_EDIT',           'Edit guest profiles',                   'guest'),
    ('10000000-0000-0000-0000-000000000019', 'GUEST_MANAGE',         'Manage guests (GDPR erasure)',          'guest'),
    -- Messages
    ('10000000-0000-0000-0000-000000000020', 'MESSAGE_VIEW',         'View inbox messages',                   'guest'),
    ('10000000-0000-0000-0000-000000000021', 'MESSAGE_SEND',         'Send messages',                         'guest'),
    ('10000000-0000-0000-0000-000000000022', 'MESSAGE_MANAGE',       'Manage inbox (assign, snooze, label)',  'guest'),
    -- Channel
    ('10000000-0000-0000-0000-000000000023', 'CHANNEL_VIEW',         'View channel connections',              'channel'),
    ('10000000-0000-0000-0000-000000000024', 'CHANNEL_MANAGE',       'Manage channel connections',            'channel'),
    -- Payment
    ('10000000-0000-0000-0000-000000000025', 'PAYMENT_VIEW',         'View payments and invoices',            'payment'),
    ('10000000-0000-0000-0000-000000000026', 'PAYMENT_MANAGE',       'Manage payments and refunds',           'payment'),
    -- Subscription
    ('10000000-0000-0000-0000-000000000027', 'SUBSCRIPTION_VIEW',    'View subscription plan',                'subscription'),
    ('10000000-0000-0000-0000-000000000028', 'SUBSCRIPTION_MANAGE',  'Manage subscription plan',              'subscription'),
    -- Housekeeping
    ('10000000-0000-0000-0000-000000000029', 'HOUSEKEEPING_VIEW',    'View cleaning tasks',                   'housekeeping'),
    ('10000000-0000-0000-0000-000000000030', 'HOUSEKEEPING_MANAGE',  'Manage cleaning tasks',                 'housekeeping'),
    -- Maintenance
    ('10000000-0000-0000-0000-000000000031', 'MAINTENANCE_VIEW',     'View maintenance issues',               'maintenance'),
    ('10000000-0000-0000-0000-000000000032', 'MAINTENANCE_MANAGE',   'Manage maintenance issues',             'maintenance'),
    -- Reporting
    ('10000000-0000-0000-0000-000000000033', 'REPORT_VIEW',          'View reports and analytics',            'reporting'),
    -- Owner Portal
    ('10000000-0000-0000-0000-000000000034', 'OWNER_VIEW',           'View owner portal',                     'owner'),
    ('10000000-0000-0000-0000-000000000035', 'OWNER_MANAGE',         'Manage owner settings',                 'owner'),
    -- Audit
    ('10000000-0000-0000-0000-000000000036', 'AUDIT_VIEW',           'View audit trail',                      'audit'),
    -- Notification
    ('10000000-0000-0000-0000-000000000037', 'NOTIFICATION_VIEW',    'View notifications',                    'notification'),
    ('10000000-0000-0000-0000-000000000038', 'NOTIFICATION_MANAGE',  'Manage notification settings',          'notification'),
    -- Data Import
    ('10000000-0000-0000-0000-000000000039', 'IMPORT_MANAGE',        'Run data imports',                      'dataimport');

-- ============================================================
-- Role-Permission mappings
-- ============================================================

-- SUPER_ADMIN: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', id FROM permissions;

-- AGENCY_ADMIN: all tenant-level permissions (same as super admin minus cross-tenant)
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id FROM permissions;

-- PROPERTY_MANAGER: operational permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003', id FROM permissions
WHERE code IN (
    'TENANT_VIEW',
    'PROPERTY_CREATE', 'PROPERTY_VIEW', 'PROPERTY_EDIT',
    'BOOKING_CREATE', 'BOOKING_VIEW', 'BOOKING_EDIT', 'BOOKING_CANCEL',
    'RATE_MANAGE', 'COUPON_MANAGE',
    'GUEST_CREATE', 'GUEST_VIEW', 'GUEST_EDIT', 'GUEST_MANAGE',
    'MESSAGE_VIEW', 'MESSAGE_SEND', 'MESSAGE_MANAGE',
    'CHANNEL_VIEW', 'CHANNEL_MANAGE',
    'PAYMENT_VIEW', 'PAYMENT_MANAGE',
    'HOUSEKEEPING_VIEW', 'HOUSEKEEPING_MANAGE',
    'MAINTENANCE_VIEW', 'MAINTENANCE_MANAGE',
    'REPORT_VIEW',
    'NOTIFICATION_VIEW'
);

-- PROPERTY_OWNER: read-only + calendar blocking
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000004', id FROM permissions
WHERE code IN (
    'TENANT_VIEW',
    'PROPERTY_VIEW',
    'BOOKING_VIEW',
    'PAYMENT_VIEW',
    'REPORT_VIEW',
    'OWNER_VIEW',
    'NOTIFICATION_VIEW'
);

-- HOUSEKEEPER: cleaning tasks only
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000005', id FROM permissions
WHERE code IN (
    'HOUSEKEEPING_VIEW', 'HOUSEKEEPING_MANAGE',
    'NOTIFICATION_VIEW'
);

-- GUEST: own booking access
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000006', id FROM permissions
WHERE code IN (
    'BOOKING_VIEW',
    'MESSAGE_VIEW', 'MESSAGE_SEND',
    'NOTIFICATION_VIEW'
);
