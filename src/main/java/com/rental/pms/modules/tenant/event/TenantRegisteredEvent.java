package com.rental.pms.modules.tenant.event;

import com.rental.pms.common.event.DomainEvent;

import java.util.UUID;

public record TenantRegisteredEvent(
        DomainEvent domainEvent,
        UUID tenantId,
        String tenantName,
        String slug,
        UUID adminUserId
) {}
