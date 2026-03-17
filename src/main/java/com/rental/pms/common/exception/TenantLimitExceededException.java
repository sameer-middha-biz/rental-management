package com.rental.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a tenant exceeds a subscription-enforced limit.
 * Examples: property count exceeds plan, user seats exceeded.
 * Maps to HTTP 409 Conflict.
 */
public class TenantLimitExceededException extends BaseBusinessException {

    public TenantLimitExceededException(String resourceType, int currentCount, int maxAllowed) {
        super(
                String.format("Tenant limit exceeded for %s: current=%d, max=%d",
                        resourceType, currentCount, maxAllowed),
                "TENANT.LIMIT.EXCEEDED",
                HttpStatus.CONFLICT
        );
    }
}
