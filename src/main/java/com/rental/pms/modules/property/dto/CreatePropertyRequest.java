package com.rental.pms.modules.property.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

public record CreatePropertyRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String slug,
        String description,
        @NotBlank
        @Pattern(regexp = "APARTMENT|VILLA|COTTAGE|HOUSE|STUDIO|OTHER",
                message = "propertyType must be one of APARTMENT, VILLA, COTTAGE, HOUSE, STUDIO, OTHER")
        String propertyType,
        UUID ownerId,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String stateProvince,
        @Size(max = 20) String postalCode,
        @Size(min = 3, max = 3, message = "country must be ISO 3166-1 alpha-3 (3 letters)") String country,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Positive Integer maxGuests,
        @PositiveOrZero Integer bedrooms,
        @PositiveOrZero Integer bathrooms,
        @PositiveOrZero Integer beds,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        String internalNotes
) {
}
