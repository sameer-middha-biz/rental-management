package com.rental.pms.modules.guest.event;

import com.rental.pms.common.event.DomainEvent;

import java.util.UUID;

public record GuestCreatedEvent(
        DomainEvent meta,
        UUID tenantId,
        UUID guestId
) {
}
