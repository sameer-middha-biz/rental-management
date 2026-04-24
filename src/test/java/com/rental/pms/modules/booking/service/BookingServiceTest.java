package com.rental.pms.modules.booking.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.event.DomainEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.booking.dto.BookingResponse;
import com.rental.pms.modules.booking.dto.CreateBookingRequest;
import com.rental.pms.modules.booking.dto.UpdateBookingStatusRequest;
import com.rental.pms.modules.booking.entity.Booking;
import com.rental.pms.modules.booking.entity.BookingNightlyRate;
import com.rental.pms.modules.booking.entity.BookingStatus;
import com.rental.pms.modules.booking.event.BookingCancelledEvent;
import com.rental.pms.modules.booking.event.BookingCreatedEvent;
import com.rental.pms.modules.booking.event.BookingStatusChangedEvent;
import com.rental.pms.modules.booking.mapper.BookingMapper;
import com.rental.pms.modules.booking.repository.BookingNightlyRateRepository;
import com.rental.pms.modules.booking.repository.BookingRepository;
import com.rental.pms.modules.guest.entity.Guest;
import com.rental.pms.modules.guest.repository.GuestRepository;
import com.rental.pms.modules.pricing.dto.NightlyRateDto;
import com.rental.pms.modules.pricing.dto.PriceBreakdownResponse;
import com.rental.pms.modules.pricing.service.PricingService;
import com.rental.pms.modules.property.entity.Property;
import com.rental.pms.modules.property.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BookingService Tests")
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingNightlyRateRepository nightlyRateRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private GuestRepository guestRepository;
    @Mock private AvailabilityService availabilityService;
    @Mock private PricingService pricingService;
    @Mock private BookingMapper bookingMapper;
    @Mock private DomainEventPublisher domainEventPublisher;
    @Mock private AuditEventPublisher auditEventPublisher;
    @Mock private CurrentUser currentUser;

    @InjectMocks private BookingService bookingService;

    private UUID tenantId;
    private UUID propertyId;
    private UUID guestId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
        guestId = UUID.randomUUID();

        given(currentUser.getTenantId()).willReturn(tenantId);
        given(currentUser.getUserId()).willReturn(UUID.randomUUID());
        Property p = Property.builder().build();
        p.setId(propertyId);
        given(propertyRepository.findByIdAndTenantId(propertyId, tenantId)).willReturn(Optional.of(p));
        Guest g = Guest.builder().firstName("A").lastName("B").build();
        g.setId(guestId);
        given(guestRepository.findByIdAndTenantId(guestId, tenantId)).willReturn(Optional.of(g));
        given(bookingMapper.toResponse(any(Booking.class), anyList()))
                .willReturn(mockResponse());
    }

    private BookingResponse mockResponse() {
        return new BookingResponse(UUID.randomUUID(), tenantId, propertyId, guestId, "BK-ABC123",
                LocalDate.of(2030, 7, 1), LocalDate.of(2030, 7, 4),
                BookingStatus.CONFIRMED, 2, 30000L, "GBP",
                com.rental.pms.modules.booking.entity.BookingSource.DIRECT, null,
                null, null, null, null, List.of(), null, null);
    }

    private PriceBreakdownResponse sampleQuote() {
        return new PriceBreakdownResponse(
                List.of(
                        new NightlyRateDto(LocalDate.of(2030, 7, 1), 10000L, "BASE"),
                        new NightlyRateDto(LocalDate.of(2030, 7, 2), 10000L, "BASE"),
                        new NightlyRateDto(LocalDate.of(2030, 7, 3), 10000L, "BASE")),
                30000L, List.of(), 0L, 30000L, "GBP");
    }

    @Test
    @DisplayName("create: atomic flow — lock, price, save booking + rates, publish event")
    void create_happyPath() {
        given(pricingService.calculate(any(), any(), any(), any(), any()))
                .willReturn(sampleQuote());
        given(bookingRepository.save(any(Booking.class))).willAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        willDoNothing().given(availabilityService)
                .checkAndLock(eq(tenantId), eq(propertyId), any(), any());

        CreateBookingRequest req = new CreateBookingRequest(propertyId, guestId,
                LocalDate.of(2030, 7, 1), LocalDate.of(2030, 7, 4),
                2, null, null, null, null);

        bookingService.create(req);

        verify(availabilityService).checkAndLock(eq(tenantId), eq(propertyId),
                eq(LocalDate.of(2030, 7, 1)), eq(LocalDate.of(2030, 7, 4)));
        ArgumentCaptor<List<BookingNightlyRate>> ratesCap = ArgumentCaptor.forClass(List.class);
        verify(nightlyRateRepository).saveAll(ratesCap.capture());
        assertThat(ratesCap.getValue()).hasSize(3);

        verify(domainEventPublisher).publish(any(BookingCreatedEvent.class));
        verify(auditEventPublisher).publish(eq(tenantId), any(), eq("BOOKING_CREATED"),
                eq("Booking"), any(), any());
    }

    @Test
    @DisplayName("create: availability conflict aborts before save")
    void create_availabilityConflictAborts() {
        willThrow(new ConflictException("x", "BOOKING.AVAILABILITY.CONFLICT"))
                .given(availabilityService).checkAndLock(any(), any(), any(), any());

        CreateBookingRequest req = new CreateBookingRequest(propertyId, guestId,
                LocalDate.of(2030, 7, 1), LocalDate.of(2030, 7, 4),
                2, null, null, null, null);

        assertThatThrownBy(() -> bookingService.create(req)).isInstanceOf(ConflictException.class);

        verify(bookingRepository, never()).save(any());
        verify(nightlyRateRepository, never()).saveAll(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("create: missing property → 404")
    void create_missingProperty() {
        given(propertyRepository.findByIdAndTenantId(propertyId, tenantId)).willReturn(Optional.empty());

        CreateBookingRequest req = new CreateBookingRequest(propertyId, guestId,
                LocalDate.of(2030, 7, 1), LocalDate.of(2030, 7, 4),
                2, null, null, null, null);

        assertThatThrownBy(() -> bookingService.create(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create with coupon: records coupon use after save")
    void create_withCoupon_recordsUse() {
        given(pricingService.calculate(any(), any(), any(), any(), any()))
                .willReturn(sampleQuote());
        given(bookingRepository.save(any(Booking.class))).willAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        CreateBookingRequest req = new CreateBookingRequest(propertyId, guestId,
                LocalDate.of(2030, 7, 1), LocalDate.of(2030, 7, 4),
                2, null, "vip", null, null);

        bookingService.create(req);
        verify(pricingService).recordCouponUse(tenantId, "VIP");
    }

    @Test
    @DisplayName("changeStatus CONFIRMED -> CHECKED_IN: allowed, publishes StatusChanged")
    void changeStatus_allowedTransition() {
        Booking existing = Booking.builder().status(BookingStatus.CONFIRMED)
                .bookingReference("BK-X").build();
        existing.setId(UUID.randomUUID());
        given(bookingRepository.findByIdAndTenantId(existing.getId(), tenantId))
                .willReturn(Optional.of(existing));

        bookingService.changeStatus(existing.getId(),
                new UpdateBookingStatusRequest(BookingStatus.CHECKED_IN, null));

        assertThat(existing.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        verify(domainEventPublisher).publish(any(BookingStatusChangedEvent.class));
    }

    @Test
    @DisplayName("changeStatus CONFIRMED -> CANCELLED: sets cancelledAt and publishes Cancelled + StatusChanged")
    void changeStatus_cancel() {
        Booking existing = Booking.builder().status(BookingStatus.CONFIRMED)
                .bookingReference("BK-Y").build();
        existing.setId(UUID.randomUUID());
        given(bookingRepository.findByIdAndTenantId(existing.getId(), tenantId))
                .willReturn(Optional.of(existing));

        bookingService.changeStatus(existing.getId(),
                new UpdateBookingStatusRequest(BookingStatus.CANCELLED, "guest no-show"));

        assertThat(existing.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(existing.getCancelledAt()).isNotNull();
        assertThat(existing.getCancelledReason()).isEqualTo("guest no-show");
        verify(domainEventPublisher).publish(any(BookingCancelledEvent.class));
        verify(domainEventPublisher).publish(any(BookingStatusChangedEvent.class));
    }

    @Test
    @DisplayName("changeStatus: illegal transition → 409")
    void changeStatus_illegal() {
        Booking existing = Booking.builder().status(BookingStatus.CHECKED_OUT)
                .bookingReference("BK-Z").build();
        existing.setId(UUID.randomUUID());
        given(bookingRepository.findByIdAndTenantId(existing.getId(), tenantId))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> bookingService.changeStatus(existing.getId(),
                new UpdateBookingStatusRequest(BookingStatus.CONFIRMED, null)))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("BOOKING.ILLEGAL_TRANSITION");
    }

    @Test
    @DisplayName("getById: not found → 404")
    void getById_notFound() {
        UUID id = UUID.randomUUID();
        given(bookingRepository.findByIdAndTenantId(id, tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
