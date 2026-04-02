package com.rental.pms.modules.tenant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String slug,
        String customDomain,
        String timezone,
        String defaultCurrency,
        String managementFeeType,
        BigDecimal managementFeePercentage,
        Long managementFeeFixed,
        String logoS3Key,
        String contactEmail,
        String contactPhone,
        String address,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
