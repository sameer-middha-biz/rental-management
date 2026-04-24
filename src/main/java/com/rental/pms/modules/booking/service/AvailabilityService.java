package com.rental.pms.modules.booking.service;

import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.booking.dto.AvailabilityResponse;
import com.rental.pms.modules.booking.entity.Booking;
import com.rental.pms.modules.booking.repository.AdvisoryLockRepository;
import com.rental.pms.modules.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Concurrency-safe availability checks.
 * <p>
 * {@link #checkAndLock} is the critical-section entry point used by BookingService: it takes a
 * per-property advisory lock, then queries for overlapping active bookings. Because the lock is
 * transaction-scoped, any second caller hitting the same property waits until the first caller's
 * transaction commits — eliminating the classic TOCTOU race where two requests both see "free"
 * and both insert.
 * <p>
 * {@link #getAvailability} is a read-only snapshot; it does not lock.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    private final BookingRepository bookingRepository;
    private final AdvisoryLockRepository advisoryLockRepository;
    private final CurrentUser currentUser;

    /**
     * Must be called inside an active transaction (Propagation.MANDATORY enforces that).
     * Acquires the lock first, then checks overlap. Throws {@link ConflictException} on conflict.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkAndLock(UUID tenantId, UUID propertyId, LocalDate checkIn, LocalDate checkOut) {
        validateRange(checkIn, checkOut);

        advisoryLockRepository.lockProperty(propertyId);

        if (bookingRepository.existsOverlapping(tenantId, propertyId, checkIn, checkOut)) {
            log.debug("Availability conflict: propertyId={}, {} - {}", propertyId, checkIn, checkOut);
            throw new ConflictException(
                    "The property is not available for the selected dates",
                    "BOOKING.AVAILABILITY.CONFLICT");
        }
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(UUID propertyId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        UUID tenantId = currentUser.getTenantId();

        List<Booking> overlapping = bookingRepository.findOverlapping(tenantId, propertyId, from, to);
        List<AvailabilityResponse.BlockedRange> blocked = overlapping.stream()
                .map(b -> new AvailabilityResponse.BlockedRange(b.getCheckIn(), b.getCheckOut()))
                .toList();

        return new AvailabilityResponse(propertyId, from, to, blocked.isEmpty(), blocked);
    }

    private void validateRange(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }
    }
}
