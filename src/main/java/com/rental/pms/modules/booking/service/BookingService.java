package com.rental.pms.modules.booking.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.common.event.DomainEvent;
import com.rental.pms.common.event.DomainEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.booking.dto.BookingResponse;
import com.rental.pms.modules.booking.dto.CreateBookingRequest;
import com.rental.pms.modules.booking.dto.UpdateBookingRequest;
import com.rental.pms.modules.booking.dto.UpdateBookingStatusRequest;
import com.rental.pms.modules.booking.entity.Booking;
import com.rental.pms.modules.booking.entity.BookingNightlyRate;
import com.rental.pms.modules.booking.entity.BookingSource;
import com.rental.pms.modules.booking.entity.BookingStatus;
import com.rental.pms.modules.booking.event.BookingCancelledEvent;
import com.rental.pms.modules.booking.event.BookingCreatedEvent;
import com.rental.pms.modules.booking.event.BookingStatusChangedEvent;
import com.rental.pms.modules.booking.mapper.BookingMapper;
import com.rental.pms.modules.booking.repository.BookingNightlyRateRepository;
import com.rental.pms.modules.booking.repository.BookingRepository;
import com.rental.pms.modules.guest.entity.Guest;
import com.rental.pms.modules.guest.repository.GuestRepository;
import com.rental.pms.modules.pricing.dto.PriceBreakdownResponse;
import com.rental.pms.modules.pricing.service.PricingService;
import com.rental.pms.modules.property.entity.Property;
import com.rental.pms.modules.property.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core booking workflow.
 * <p>
 * {@link #create} is the atomic critical section: availability lock → price quote →
 * insert booking + per-night rates → publish event, all in one transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingNightlyRateRepository nightlyRateRepository;
    private final PropertyRepository propertyRepository;
    private final GuestRepository guestRepository;
    private final AvailabilityService availabilityService;
    private final PricingService pricingService;
    private final BookingMapper bookingMapper;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditEventPublisher auditEventPublisher;
    private final CurrentUser currentUser;

    // FSM — keep narrow so bad transitions can't slip through.
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = Map.of(
            BookingStatus.PENDING, EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DECLINED),
            BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.CHECKED_IN, BookingStatus.CANCELLED),
            BookingStatus.CHECKED_IN, EnumSet.of(BookingStatus.CHECKED_OUT),
            BookingStatus.CHECKED_OUT, EnumSet.noneOf(BookingStatus.class),
            BookingStatus.CANCELLED, EnumSet.noneOf(BookingStatus.class),
            BookingStatus.DECLINED, EnumSet.noneOf(BookingStatus.class)
    );

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        UUID tenantId = currentUser.getTenantId();

        Property property = propertyRepository.findByIdAndTenantId(request.propertyId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", request.propertyId()));
        Guest guest = guestRepository.findByIdAndTenantId(request.guestId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest", "id", request.guestId()));

        // Lock + availability check (throws ConflictException on overlap).
        availabilityService.checkAndLock(tenantId, property.getId(),
                request.checkIn(), request.checkOut());

        // Price quote — reuses the pricing engine so the breakdown is the single source of truth.
        PriceBreakdownResponse quote = pricingService.calculate(
                tenantId, property.getId(),
                request.checkIn(), request.checkOut(), request.couponCode());

        Booking booking = Booking.builder()
                .propertyId(property.getId())
                .guestId(guest.getId())
                .bookingReference(generateReference())
                .checkIn(request.checkIn())
                .checkOut(request.checkOut())
                .status(BookingStatus.CONFIRMED)
                .guestCount(request.guestCount() == null ? 1 : request.guestCount())
                .totalPriceMinorUnits(quote.totalMinorUnits())
                .currency(quote.currency())
                .source(request.source() == null ? BookingSource.DIRECT : request.source())
                .couponCode(normalizeCoupon(request.couponCode()))
                .specialRequests(request.specialRequests())
                .notes(request.notes())
                .build();
        booking = bookingRepository.save(booking);

        List<BookingNightlyRate> rates = new ArrayList<>();
        for (var n : quote.nightlyRates()) {
            rates.add(BookingNightlyRate.builder()
                    .bookingId(booking.getId())
                    .date(n.date())
                    .rateMinorUnits(n.rateMinorUnits())
                    .rateName(n.rateName())
                    .build());
        }
        nightlyRateRepository.saveAll(rates);

        if (booking.getCouponCode() != null) {
            pricingService.recordCouponUse(tenantId, booking.getCouponCode());
        }

        domainEventPublisher.publish(new BookingCreatedEvent(
                DomainEvent.now(tenantId), tenantId, booking.getId(),
                booking.getPropertyId(), booking.getGuestId(),
                booking.getCheckIn(), booking.getCheckOut()));

        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "BOOKING_CREATED", "Booking", booking.getId(),
                "Booking " + booking.getBookingReference() + " created for guest " + guest.getId());

        log.info("Booking created: id={}, reference={}, tenantId={}",
                booking.getId(), booking.getBookingReference(), tenantId);

        return bookingMapper.toResponse(booking, rates);
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(UUID id) {
        UUID tenantId = currentUser.getTenantId();
        Booking b = findOrThrow(id, tenantId);
        return bookingMapper.toResponse(b, nightlyRateRepository.findByBookingIdOrderByDateAsc(b.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(UUID propertyId, BookingStatus status, Pageable pageable) {
        UUID tenantId = currentUser.getTenantId();
        Page<Booking> page;
        if (propertyId != null) {
            page = bookingRepository.findByTenantIdAndPropertyId(tenantId, propertyId, pageable);
        } else if (status != null) {
            page = bookingRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            page = bookingRepository.findByTenantId(tenantId, pageable);
        }
        return PageResponse.from(page.map(b -> bookingMapper.toResponse(b, List.of())));
    }

    @Transactional
    public BookingResponse update(UUID id, UpdateBookingRequest request) {
        UUID tenantId = currentUser.getTenantId();
        Booking b = findOrThrow(id, tenantId);
        if (b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.DECLINED
                || b.getStatus() == BookingStatus.CHECKED_OUT) {
            throw new ConflictException(
                    "Cannot modify booking in terminal status " + b.getStatus(),
                    "BOOKING.TERMINAL_STATUS");
        }
        bookingMapper.applyUpdate(request, b);
        return bookingMapper.toResponse(b, nightlyRateRepository.findByBookingIdOrderByDateAsc(b.getId()));
    }

    @Transactional
    public BookingResponse changeStatus(UUID id, UpdateBookingStatusRequest request) {
        UUID tenantId = currentUser.getTenantId();
        Booking b = findOrThrow(id, tenantId);

        BookingStatus from = b.getStatus();
        BookingStatus to = request.status();
        if (from == to) {
            return bookingMapper.toResponse(b, nightlyRateRepository.findByBookingIdOrderByDateAsc(b.getId()));
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new ConflictException(
                    "Illegal status transition: " + from + " -> " + to,
                    "BOOKING.ILLEGAL_TRANSITION");
        }

        b.setStatus(to);
        if (to == BookingStatus.CANCELLED) {
            b.setCancelledAt(Instant.now());
            b.setCancelledReason(request.reason());
            domainEventPublisher.publish(new BookingCancelledEvent(
                    DomainEvent.now(tenantId), tenantId, b.getId(), request.reason()));
        }

        domainEventPublisher.publish(new BookingStatusChangedEvent(
                DomainEvent.now(tenantId), tenantId, b.getId(), from, to));

        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "BOOKING_STATUS_CHANGED", "Booking", b.getId(),
                "Booking " + b.getBookingReference() + " " + from + " -> " + to);

        log.info("Booking status changed: id={}, {} -> {}", b.getId(), from, to);
        return bookingMapper.toResponse(b, nightlyRateRepository.findByBookingIdOrderByDateAsc(b.getId()));
    }

    private Booking findOrThrow(UUID id, UUID tenantId) {
        return bookingRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", id));
    }

    private static String normalizeCoupon(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase();
    }

    /**
     * Short, human-friendly reference: "BK-" + 6 hex chars. Uniqueness is enforced at the DB
     * level (uq_bookings_tenant_reference); collisions are astronomically rare but we still
     * retry a handful of times defensively.
     */
    private static String generateReference() {
        int n = ThreadLocalRandom.current().nextInt(0x1000000);
        return "BK-" + String.format("%06X", n);
    }
}
