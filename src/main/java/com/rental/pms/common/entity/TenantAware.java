package com.rental.pms.common.entity;

import java.util.UUID;

/**
 * Interface for all tenant-scoped entities.
 * Entities implementing this interface are automatically filtered by tenant_id
 * via Hibernate filters.
 */
public interface TenantAware {

    UUID getTenantId();

    void setTenantId(UUID tenantId);
}
