package com.rental.pms.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base record for all domain events published via the event bus.
 * All module-specific events should include these common fields.
 *
 * @param eventId   unique identifier for this event occurrence
 * @param tenantId  tenant that generated this event
 * @param occurredAt when this event occurred
 */
public record DomainEvent(
        UUID eventId,
        UUID tenantId,
        Instant occurredAt
) {

    /**
     * Creates a new DomainEvent with auto-generated ID and current timestamp.
     */
    public static DomainEvent now(UUID tenantId) {
        return new DomainEvent(UUID.randomUUID(), tenantId, Instant.now());
    }
}
