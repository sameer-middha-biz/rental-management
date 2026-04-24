package com.rental.pms.modules.booking.dto;

import com.rental.pms.modules.booking.entity.BookingSource;
import com.rental.pms.modules.booking.entity.BookingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID tenantId,
        UUID propertyId,
        UUID guestId,
        String bookingReference,
        LocalDate checkIn,
        LocalDate checkOut,
        BookingStatus status,
        Integer guestCount,
        Long totalPriceMinorUnits,
        String currency,
        BookingSource source,
        String couponCode,
        String specialRequests,
        String notes,
        Instant cancelledAt,
        String cancelledReason,
        List<NightlyRate> nightlyRates,
        Instant createdAt,
        Instant updatedAt
) {
    public record NightlyRate(LocalDate date, long rateMinorUnits, String rateName) {}
}
