package com.rental.pms.common.security;

import java.util.UUID;

/**
 * ThreadLocal holder for the current tenant ID.
 * Set by TenantFilter from JWT claims on each request.
 * Must be cleared after each request to prevent tenant leakage.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Utility class — no instantiation
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
