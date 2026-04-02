package com.rental.pms.modules.user.event;

import com.rental.pms.common.event.DomainEvent;

import java.util.UUID;

public record UserDeletedEvent(
        DomainEvent domainEvent,
        UUID tenantId,
        UUID userId,
        String email
) {}
