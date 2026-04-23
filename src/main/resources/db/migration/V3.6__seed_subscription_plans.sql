-- Seed the three MVP subscription plans.
-- Fixed UUIDs so code and tests can reference them deterministically.
-- NULL limits = unlimited.
INSERT INTO subscription_plans (id, code, name, description, max_properties, max_users,
                                monthly_price_minor_units, currency, features, sort_order) VALUES
    ('20000000-0000-0000-0000-000000000001', 'STARTER', 'Starter',
        'Free tier for small agencies getting started',
        5, 5, 0, 'GBP',
        '{"ical_sync": true, "direct_booking": false, "owner_portal": false, "reporting": "basic", "api_access": false}'::jsonb,
        1),
    ('20000000-0000-0000-0000-000000000002', 'PRO', 'Pro',
        'For growing agencies managing multiple properties',
        25, 20, 4900, 'GBP',
        '{"ical_sync": true, "direct_booking": true, "owner_portal": true, "reporting": "advanced", "api_access": false}'::jsonb,
        2),
    ('20000000-0000-0000-0000-000000000003', 'AGENCY', 'Agency',
        'Unlimited properties, full feature set, priority support',
        NULL, NULL, 14900, 'GBP',
        '{"ical_sync": true, "direct_booking": true, "owner_portal": true, "reporting": "advanced", "api_access": true}'::jsonb,
        3);
