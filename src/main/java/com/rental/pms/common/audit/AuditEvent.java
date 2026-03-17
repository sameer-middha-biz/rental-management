package com.rental.pms.common.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring ApplicationEvent payload for audit trail entries.
 * Published by services when auditable actions occur.
 * Consumed by the Audit module listener (Phase 8).
 *
 * @param eventId    unique event identifier
 * @param tenantId   tenant where the action occurred
 * @param userId     user who performed the action
 * @param action     action type (e.g., "BOOKING_CREATED", "PROPERTY_UPDATED")
 * @param entityType entity type affected (e.g., "Booking", "Property")
 * @param entityId   ID of the affected entity
 * @param details    additional context (JSON string or description)
 * @param occurredAt when the action occurred
 */
public record AuditEvent(
        UUID eventId,
        UUID tenantId,
        UUID userId,
        String action,
        String entityType,
        UUID entityId,
        String details,
        Instant occurredAt
) {

    public static AuditEvent of(UUID tenantId, UUID userId, String action,
                                 String entityType, UUID entityId, String details) {
        return new AuditEvent(
                UUID.randomUUID(),
                tenantId,
                userId,
                action,
                entityType,
                entityId,
                details,
                Instant.now()
        );
    }
}
