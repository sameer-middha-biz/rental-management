package com.rental.pms.modules.booking.dto;

import com.rental.pms.modules.booking.entity.BookingSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID propertyId,
        @NotNull UUID guestId,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @Min(1) Integer guestCount,
        BookingSource source,
        String couponCode,
        String specialRequests,
        String notes
) {}
