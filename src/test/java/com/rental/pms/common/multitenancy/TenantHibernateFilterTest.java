package com.rental.pms.common.multitenancy;

import com.rental.pms.common.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantHibernateFilterTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private Filter filter;

    private TenantHibernateFilter tenantHibernateFilter;

    @BeforeEach
    void setUp() {
        tenantHibernateFilter = new TenantHibernateFilter(entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void enableFilter_WhenTenantContextSet_ShouldActivateHibernateFilterWithTenantId() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        doReturn(session).when(entityManager).unwrap(Session.class);
        when(session.enableFilter("tenantFilter")).thenReturn(filter);

        tenantHibernateFilter.enableFilter();

        verify(session).enableFilter("tenantFilter");
        verify(filter).setParameter("tenantId", tenantId);
    }

    @Test
    void enableFilter_WhenTenantContextNull_ShouldNotActivateFilter() {
        // TenantContext is not set (null)

        tenantHibernateFilter.enableFilter();

        verifyNoInteractions(entityManager);
    }

    @Test
    void disableFilter_ShouldDeactivateHibernateFilter() {
        doReturn(session).when(entityManager).unwrap(Session.class);

        tenantHibernateFilter.disableFilter();

        verify(session).disableFilter("tenantFilter");
    }
}
