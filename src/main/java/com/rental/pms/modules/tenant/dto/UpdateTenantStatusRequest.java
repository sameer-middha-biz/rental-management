package com.rental.pms.modules.tenant.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateTenantStatusRequest(
        @NotNull String status
) {}
