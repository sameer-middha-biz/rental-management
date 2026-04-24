package com.rental.pms.modules.booking.event;

import com.rental.pms.common.event.DomainEvent;

import java.util.UUID;

public record BookingCancelledEvent(
        DomainEvent meta,
        UUID tenantId,
        UUID bookingId,
        String reason
) {}
