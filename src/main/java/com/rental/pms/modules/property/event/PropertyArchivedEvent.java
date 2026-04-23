package com.rental.pms.modules.property.event;

import com.rental.pms.common.event.DomainEvent;

import java.util.UUID;

public record PropertyArchivedEvent(
        DomainEvent meta,
        UUID tenantId,
        UUID propertyId
) {
}
