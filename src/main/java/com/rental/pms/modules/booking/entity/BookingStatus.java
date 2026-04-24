package com.rental.pms.modules.booking.entity;

/**
 * Booking lifecycle. FSM:
 *   PENDING     -> CONFIRMED | CANCELLED | DECLINED
 *   CONFIRMED   -> CHECKED_IN | CANCELLED
 *   CHECKED_IN  -> CHECKED_OUT
 *   CHECKED_OUT -> (terminal)
 *   CANCELLED   -> (terminal)
 *   DECLINED    -> (terminal)
 * AvailabilityService treats CANCELLED and DECLINED as "slot free"; all others block overlap.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED,
    DECLINED
}
