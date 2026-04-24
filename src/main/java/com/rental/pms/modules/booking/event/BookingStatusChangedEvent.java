package com.rental.pms.modules.booking.event;

import com.rental.pms.common.event.DomainEvent;
import com.rental.pms.modules.booking.entity.BookingStatus;

import java.util.UUID;

public record BookingStatusChangedEvent(
        DomainEvent meta,
        UUID tenantId,
        UUID bookingId,
        BookingStatus previousStatus,
        BookingStatus newStatus
) {}
