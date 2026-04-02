package com.rental.pms.modules.user.event;

import com.rental.pms.common.event.DomainEvent;

import java.util.UUID;

public record UserCreatedEvent(
        DomainEvent domainEvent,
        UUID tenantId,
        UUID userId,
        String email,
        String source
) {
    public static final String SOURCE_REGISTRATION = "REGISTRATION";
    public static final String SOURCE_INVITATION = "INVITATION";
}
