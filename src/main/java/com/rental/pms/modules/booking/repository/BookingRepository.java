package com.rental.pms.modules.booking.repository;

import com.rental.pms.modules.booking.entity.Booking;
import com.rental.pms.modules.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Returns bookings that overlap the half-open range [checkIn, checkOut) on a property,
     * excluding terminal non-blocking statuses (CANCELLED, DECLINED).
     * Overlap is: existing.check_in &lt; requested.check_out AND existing.check_out &gt; requested.check_in.
     */
    @Query("""
           SELECT b FROM Booking b
           WHERE b.tenantId = :tenantId
             AND b.propertyId = :propertyId
             AND b.status NOT IN (com.rental.pms.modules.booking.entity.BookingStatus.CANCELLED,
                                  com.rental.pms.modules.booking.entity.BookingStatus.DECLINED)
             AND b.checkIn < :checkOut
             AND b.checkOut > :checkIn
           """)
    List<Booking> findOverlapping(UUID tenantId, UUID propertyId,
                                  LocalDate checkIn, LocalDate checkOut);

    /**
     * Lightweight existence check used by AvailabilityService for fast conflict detection.
     */
    @Query("""
           SELECT COUNT(b) > 0 FROM Booking b
           WHERE b.tenantId = :tenantId
             AND b.propertyId = :propertyId
             AND b.status NOT IN (com.rental.pms.modules.booking.entity.BookingStatus.CANCELLED,
                                  com.rental.pms.modules.booking.entity.BookingStatus.DECLINED)
             AND b.checkIn < :checkOut
             AND b.checkOut > :checkIn
           """)
    boolean existsOverlapping(UUID tenantId, UUID propertyId,
                              LocalDate checkIn, LocalDate checkOut);

    Page<Booking> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Booking> findByTenantIdAndStatus(UUID tenantId, BookingStatus status, Pageable pageable);

    Page<Booking> findByTenantIdAndPropertyId(UUID tenantId, UUID propertyId, Pageable pageable);
}
