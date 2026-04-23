package com.rental.pms.modules.subscription.dto;

import java.util.Map;
import java.util.UUID;

public record SubscriptionPlanResponse(
        UUID id,
        String code,
        String name,
        String description,
        Integer maxProperties,
        Integer maxUsers,
        Long monthlyPriceMinorUnits,
        String currency,
        Map<String, Object> features,
        Integer sortOrder
) {
}
