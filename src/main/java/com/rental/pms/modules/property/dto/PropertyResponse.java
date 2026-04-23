package com.rental.pms.modules.property.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record PropertyResponse(
        UUID id,
        UUID tenantId,
        UUID ownerId,
        String name,
        String slug,
        String description,
        String propertyType,
        String addressLine1,
        String addressLine2,
        String city,
        String stateProvince,
        String postalCode,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer maxGuests,
        Integer bedrooms,
        Integer bathrooms,
        Integer beds,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        String internalNotes,
        UUID defaultHousekeeperId,
        String status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
