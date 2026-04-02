package com.rental.pms.modules.user.dto;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        String email,
        String roleName,
        String status,
        Instant expiresAt,
        Instant createdAt
) {}
