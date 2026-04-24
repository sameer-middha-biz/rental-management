package com.rental.pms.modules.booking.entity;

/** Origin of a booking. Channel modules (Phase 6) write AIRBNB / BOOKING_COM. */
public enum BookingSource {
    DIRECT,
    AIRBNB,
    BOOKING_COM,
    MANUAL
}
