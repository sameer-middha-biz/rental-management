package com.rental.pms.modules.property.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * PATCH-style partial update. All fields nullable; only non-null values are applied.
 * The slug field is intentionally omitted — slugs are immutable once generated.
 */
public record UpdatePropertyRequest(
        @Size(max = 200) String name,
        String description,
        @Pattern(regexp = "APARTMENT|VILLA|COTTAGE|HOUSE|STUDIO|OTHER") String propertyType,
        UUID ownerId,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String stateProvince,
        @Size(max = 20) String postalCode,
        @Size(min = 3, max = 3) String country,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Positive Integer maxGuests,
        @PositiveOrZero Integer bedrooms,
        @PositiveOrZero Integer bathrooms,
        @PositiveOrZero Integer beds,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        String internalNotes,
        UUID defaultHousekeeperId
) {
}
