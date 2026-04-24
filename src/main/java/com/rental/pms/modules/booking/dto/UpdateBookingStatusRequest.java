package com.rental.pms.modules.booking.dto;

import com.rental.pms.modules.booking.entity.BookingStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateBookingStatusRequest(
        @NotNull BookingStatus status,
        String reason   // required when status = CANCELLED
) {}
