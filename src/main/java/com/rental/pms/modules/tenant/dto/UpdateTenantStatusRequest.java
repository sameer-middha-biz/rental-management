package com.rental.pms.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateTenantStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|SUSPENDED|CANCELLED", message = "Status must be ACTIVE, SUSPENDED, or CANCELLED")
        String status
) {}
