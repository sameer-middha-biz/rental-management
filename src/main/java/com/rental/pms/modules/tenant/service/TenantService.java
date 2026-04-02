package com.rental.pms.modules.tenant.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.tenant.dto.TenantResponse;
import com.rental.pms.modules.tenant.dto.UpdateTenantRequest;
import com.rental.pms.modules.tenant.dto.UpdateTenantStatusRequest;
import com.rental.pms.modules.tenant.entity.Tenant;
import com.rental.pms.modules.tenant.entity.TenantStatus;
import com.rental.pms.modules.tenant.mapper.TenantMapper;
import com.rental.pms.modules.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final CurrentUser currentUser;
    private final AuditEventPublisher auditEventPublisher;

    public TenantResponse getTenant() {
        Tenant tenant = findTenantByCurrentUser();
        return tenantMapper.toResponse(tenant);
    }

    @Transactional
    public TenantResponse updateTenant(UpdateTenantRequest request) {
        Tenant tenant = findTenantByCurrentUser();
        tenantMapper.updateFromRequest(request, tenant);
        tenant = tenantRepository.save(tenant);

        auditEventPublisher.publish(tenant.getId(), currentUser.getUserId(),
                "TENANT_UPDATED", "Tenant", tenant.getId(), "Tenant settings updated");

        return tenantMapper.toResponse(tenant);
    }

    // --- Super Admin endpoints ---

    public PageResponse<TenantResponse> getAllTenants(Pageable pageable) {
        Page<TenantResponse> page = tenantRepository.findAll(pageable)
                .map(tenantMapper::toResponse);
        return PageResponse.from(page);
    }

    public TenantResponse getTenantById(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", id));
        return tenantMapper.toResponse(tenant);
    }

    @Transactional
    public TenantResponse updateTenantStatus(UUID id, UpdateTenantStatusRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", id));

        TenantStatus newStatus = TenantStatus.valueOf(request.status());
        tenant.setStatus(newStatus);
        tenant = tenantRepository.save(tenant);

        log.info("Tenant status updated: tenantId={}, newStatus={}", id, newStatus);

        return tenantMapper.toResponse(tenant);
    }

    private Tenant findTenantByCurrentUser() {
        UUID tenantId = currentUser.getTenantId();
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
    }
}
