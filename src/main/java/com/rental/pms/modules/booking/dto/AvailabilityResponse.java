package com.rental.pms.modules.booking.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Availability snapshot for a property over a date window.
 * {@code blockedRanges} are closed-open intervals [start, end) of confirmed / in-progress bookings.
 */
public record AvailabilityResponse(
        UUID propertyId,
        LocalDate windowStart,
        LocalDate windowEnd,
        boolean available,
        List<BlockedRange> blockedRanges
) {
    public record BlockedRange(LocalDate checkIn, LocalDate checkOut) {}
}
