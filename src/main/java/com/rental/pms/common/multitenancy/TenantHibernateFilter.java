package com.rental.pms.common.multitenancy;

import com.rental.pms.common.security.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Activates the Hibernate @Filter for tenant isolation on each request.
 * Called by the TenantFilter (Phase 2) after setting TenantContext from JWT.
 * Super Admin requests skip filter activation for cross-tenant access.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantHibernateFilter {

    private final EntityManager entityManager;

    /**
     * Enables the Hibernate tenant filter for the current session
     * using the tenant ID from TenantContext.
     */
    public void enableFilter() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter")
                    .setParameter("tenantId", tenantId);
            log.debug("Tenant filter enabled for tenant: {}", tenantId);
        }
    }

    /**
     * Disables the Hibernate tenant filter for the current session.
     */
    public void disableFilter() {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        log.debug("Tenant filter disabled");
    }
}
