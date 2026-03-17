package com.rental.pms.common.multitenancy;

import com.rental.pms.common.entity.TenantAware;
import com.rental.pms.common.security.TenantContext;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.util.UUID;

/**
 * JPA Entity Listener that enforces tenant isolation on write operations.
 * <ul>
 *   <li>{@code @PrePersist}: Sets tenant_id from TenantContext on new entities</li>
 *   <li>{@code @PreUpdate}: Validates tenant_id has not been tampered with</li>
 * </ul>
 */
public class TenantInterceptor {

    @PrePersist
    public void setTenantOnCreate(Object entity) {
        if (entity instanceof TenantAware tenantAware) {
            UUID currentTenantId = TenantContext.getTenantId();
            if (currentTenantId == null) {
                throw new IllegalStateException(
                        "TenantContext is not set. Cannot persist tenant-scoped entity without a tenant ID.");
            }
            tenantAware.setTenantId(currentTenantId);
        }
    }

    @PreUpdate
    public void validateTenantOnUpdate(Object entity) {
        if (entity instanceof TenantAware tenantAware) {
            UUID currentTenantId = TenantContext.getTenantId();
            if (currentTenantId == null) {
                throw new IllegalStateException(
                        "TenantContext is not set. Cannot update tenant-scoped entity without a tenant ID.");
            }
            if (!currentTenantId.equals(tenantAware.getTenantId())) {
                throw new SecurityException(
                        "Attempted to modify entity belonging to a different tenant. "
                                + "Entity tenant: " + tenantAware.getTenantId()
                                + ", current tenant: " + currentTenantId);
            }
        }
    }
}
