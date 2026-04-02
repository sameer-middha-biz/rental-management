package com.rental.pms.modules.tenant.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.tenant.dto.TenantResponse;
import com.rental.pms.modules.tenant.dto.UpdateTenantRequest;
import com.rental.pms.modules.tenant.entity.Tenant;
import com.rental.pms.modules.tenant.entity.TenantStatus;
import com.rental.pms.modules.tenant.fixture.TestTenantBuilder;
import com.rental.pms.modules.tenant.mapper.TenantMapper;
import com.rental.pms.modules.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @InjectMocks
    private TenantService tenantService;

    private UUID tenantId;
    private Tenant tenant;
    private TenantResponse tenantResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = TestTenantBuilder.aTenant()
                .withId(tenantId)
                .withName("Test Agency")
                .withSlug("test-agency")
                .withContactEmail("admin@test.com")
                .build();

        tenantResponse = new TenantResponse(
                tenantId, "Test Agency", "test-agency", null, "UTC", "GBP",
                "PERCENTAGE", null, null, null, "admin@test.com", null, null,
                "ACTIVE", Instant.now(), Instant.now());
    }

    @Test
    void getTenant_ShouldReturnCurrentTenantDetails() {
        // Arrange
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantMapper.toResponse(tenant)).thenReturn(tenantResponse);

        // Act
        TenantResponse result = tenantService.getTenant();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(tenantId);
        assertThat(result.name()).isEqualTo("Test Agency");
        verify(tenantRepository).findById(tenantId);
    }

    @Test
    void getTenant_WhenNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> tenantService.getTenant())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTenant_ShouldUpdateAndReturnResponse() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UpdateTenantRequest updateRequest = new UpdateTenantRequest(
                "Updated Agency", null, "Europe/London", "EUR",
                null, null, null, "updated@test.com", null, null);

        TenantResponse updatedResponse = new TenantResponse(
                tenantId, "Updated Agency", "test-agency", null, "Europe/London", "EUR",
                "PERCENTAGE", null, null, null, "updated@test.com", null, null,
                "ACTIVE", Instant.now(), Instant.now());

        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(currentUser.getUserId()).thenReturn(userId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
        when(tenantMapper.toResponse(any(Tenant.class))).thenReturn(updatedResponse);

        // Act
        TenantResponse result = tenantService.updateTenant(updateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Updated Agency");
        assertThat(result.defaultCurrency()).isEqualTo("EUR");
        verify(tenantMapper).updateFromRequest(updateRequest, tenant);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void getTenantById_WhenNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(tenantRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> tenantService.getTenantById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
