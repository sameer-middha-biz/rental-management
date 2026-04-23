package com.rental.pms.modules.subscription.dto;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID tenantId,
        SubscriptionPlanResponse plan,
        String status,
        Instant startDate,
        Instant endDate,
        Instant trialEndsAt,
        Instant cancelledAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant createdAt
) {
}
