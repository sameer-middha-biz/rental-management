package com.rental.pms.modules.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateTenantRequest(
        @Size(min = 1, max = 200) String name,
        @Size(max = 255) String customDomain,
        @Size(max = 50) String timezone,
        @Size(min = 3, max = 3) String defaultCurrency,
        String managementFeeType,
        BigDecimal managementFeePercentage,
        Long managementFeeFixed,
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 30) String contactPhone,
        String address
) {}
