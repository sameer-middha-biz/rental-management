package com.rental.pms.modules.booking.dto;

public record UpdateBookingRequest(
        Integer guestCount,
        String specialRequests,
        String notes
) {}
