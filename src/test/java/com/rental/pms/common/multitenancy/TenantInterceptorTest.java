package com.rental.pms.common.multitenancy;

import com.rental.pms.common.entity.BaseEntity;
import com.rental.pms.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TenantInterceptorTest {

    @InjectMocks
    private TenantInterceptor interceptor;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setTenantOnCreate_WhenTenantContextSet_ShouldSetTenantIdOnEntity() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        TestEntity entity = new TestEntity();

        interceptor.setTenantOnCreate(entity);

        assertThat(entity.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void setTenantOnCreate_WhenTenantContextNotSet_ShouldThrowIllegalStateException() {
        TestEntity entity = new TestEntity();

        assertThatThrownBy(() -> interceptor.setTenantOnCreate(entity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TenantContext is not set");
    }

    @Test
    void setTenantOnCreate_WhenEntityNotTenantAware_ShouldDoNothing() {
        Object nonTenantEntity = new Object();

        // Should not throw
        interceptor.setTenantOnCreate(nonTenantEntity);
    }

    @Test
    void validateTenantOnUpdate_WhenSameTenant_ShouldNotThrow() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        TestEntity entity = new TestEntity();
        entity.setTenantId(tenantId);

        // Should not throw
        interceptor.validateTenantOnUpdate(entity);
    }

    @Test
    void validateTenantOnUpdate_WhenDifferentTenant_ShouldThrowSecurityException() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        TenantContext.setTenantId(tenantA);
        TestEntity entity = new TestEntity();
        entity.setTenantId(tenantB);

        assertThatThrownBy(() -> interceptor.validateTenantOnUpdate(entity))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("different tenant");
    }

    @Test
    void validateTenantOnUpdate_WhenTenantContextNotSet_ShouldThrowIllegalStateException() {
        TestEntity entity = new TestEntity();
        entity.setTenantId(UUID.randomUUID());

        assertThatThrownBy(() -> interceptor.validateTenantOnUpdate(entity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TenantContext is not set");
    }

    @Test
    void validateTenantOnUpdate_WhenEntityNotTenantAware_ShouldDoNothing() {
        Object nonTenantEntity = new Object();

        // Should not throw
        interceptor.validateTenantOnUpdate(nonTenantEntity);
    }

    /**
     * Concrete test entity extending BaseEntity for testing purposes.
     */
    private static class TestEntity extends BaseEntity {
    }
}
