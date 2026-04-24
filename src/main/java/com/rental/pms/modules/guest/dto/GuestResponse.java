package com.rental.pms.modules.guest.dto;

import java.time.Instant;
import java.util.UUID;

public record GuestResponse(
        UUID id,
        UUID tenantId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String nationality,
        String idDocumentS3Key,
        String notes,
        Integer totalBookings,
        Instant lastStayAt,
        boolean gdprErased,
        Instant gdprErasedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
