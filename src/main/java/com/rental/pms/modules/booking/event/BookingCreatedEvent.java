package com.rental.pms.modules.booking.event;

import com.rental.pms.common.event.DomainEvent;

import java.time.LocalDate;
import java.util.UUID;

public record BookingCreatedEvent(
        DomainEvent meta,
        UUID tenantId,
        UUID bookingId,
        UUID propertyId,
        UUID guestId,
        LocalDate checkIn,
        LocalDate checkOut
) {}
