package com.rental.pms.modules.pricing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record PriceCalculationRequest(
        @NotNull UUID propertyId,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @Min(1) Integer guestCount,
        String couponCode
) {}
