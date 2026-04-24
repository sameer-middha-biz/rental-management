package com.rental.pms.modules.booking.repository;

import com.rental.pms.modules.booking.entity.BookingNightlyRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingNightlyRateRepository extends JpaRepository<BookingNightlyRate, UUID> {
    List<BookingNightlyRate> findByBookingIdOrderByDateAsc(UUID bookingId);
}
