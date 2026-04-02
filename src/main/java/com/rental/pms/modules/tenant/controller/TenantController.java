package com.rental.pms.modules.tenant.controller;

import com.rental.pms.modules.tenant.dto.TenantResponse;
import com.rental.pms.modules.tenant.dto.UpdateTenantRequest;
import com.rental.pms.modules.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Management", description = "Manage current tenant settings")
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_VIEW')")
    @Operation(summary = "Get current tenant details")
    public ResponseEntity<TenantResponse> getTenant() {
        return ResponseEntity.ok(tenantService.getTenant());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('TENANT_MANAGE')")
    @Operation(summary = "Update current tenant settings")
    public ResponseEntity<TenantResponse> updateTenant(@Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(tenantService.updateTenant(request));
    }
}
