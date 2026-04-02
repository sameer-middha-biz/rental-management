package com.rental.pms.modules.user.event;

import com.rental.pms.common.event.DomainEvent;

import java.util.UUID;

public record InvitationCreatedEvent(
        DomainEvent domainEvent,
        UUID tenantId,
        UUID invitationId,
        String email,
        String inviteToken,
        UUID invitedBy
) {}
