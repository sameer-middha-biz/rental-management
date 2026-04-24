package com.rental.pms.modules.booking.service;

import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.booking.dto.AvailabilityResponse;
import com.rental.pms.modules.booking.entity.Booking;
import com.rental.pms.modules.booking.repository.AdvisoryLockRepository;
import com.rental.pms.modules.booking.repository.BookingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AvailabilityService Tests")
class AvailabilityServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private AdvisoryLockRepository advisoryLockRepository;
    @Mock private CurrentUser currentUser;

    @InjectMocks private AvailabilityService availabilityService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    @Test
    @DisplayName("checkAndLock acquires lock BEFORE querying overlap, passes when no overlap")
    void checkAndLock_lockFirstThenQuery() {
        given(bookingRepository.existsOverlapping(tenantId, propertyId,
                LocalDate.of(2030, 7, 1), LocalDate.of(2030, 7, 4))).willReturn(false);

        availabilityService.checkAndLock(tenantId, propertyId,
                LocalDate.of(2030, 7, 1), LocalDate.of(2030, 7, 4));

        InOrder io = inOrder(advisoryLockRepository, bookingRepository);
        io.verify(advisoryLockRepository).lockProperty(propertyId);
        io.verify(bookingRepository).existsOverlapping(any(), any(), any(), any());
    }

    @Test
    @DisplayName("checkAndLock throws 409 with code BOOKING.AVAILABILITY.CONFLICT on overlap")
    void checkAndLock_overlapConflicts() {
        given(bookingRepository.existsOverlapping(any(), any(), any(), any())).willReturn(true);

        assertThatThrownBy(() -> availabilityService.checkAndLock(tenantId, propertyId,
                LocalDate.of(2030, 7, 1), LocalDate.of(2030, 7, 4)))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("BOOKING.AVAILABILITY.CONFLICT");

        verify(advisoryLockRepository).lockProperty(propertyId);
    }

    @Test
    @DisplayName("checkAndLock rejects non-positive date range without hitting DB")
    void checkAndLock_rejectsInvalidRange() {
        assertThatThrownBy(() -> availabilityService.checkAndLock(tenantId, propertyId,
                LocalDate.of(2030, 7, 4), LocalDate.of(2030, 7, 4)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(advisoryLockRepository, bookingRepository);
    }

    @Test
    @DisplayName("getAvailability: available=true when no overlaps")
    void getAvailability_noBlocks() {
        given(currentUser.getTenantId()).willReturn(tenantId);
        given(bookingRepository.findOverlapping(tenantId, propertyId,
                LocalDate.of(2030, 8, 1), LocalDate.of(2030, 8, 10))).willReturn(List.of());

        AvailabilityResponse r = availabilityService.getAvailability(propertyId,
                LocalDate.of(2030, 8, 1), LocalDate.of(2030, 8, 10));

        assertThat(r.available()).isTrue();
        assertThat(r.blockedRanges()).isEmpty();
        assertThat(r.propertyId()).isEqualTo(propertyId);
    }

    @Test
    @DisplayName("getAvailability: maps overlapping bookings to blocked ranges")
    void getAvailability_returnsBlocks() {
        given(currentUser.getTenantId()).willReturn(tenantId);
        Booking b = Booking.builder()
                .propertyId(propertyId)
                .checkIn(LocalDate.of(2030, 8, 3))
                .checkOut(LocalDate.of(2030, 8, 5))
                .build();
        given(bookingRepository.findOverlapping(any(), any(), any(), any())).willReturn(List.of(b));

        AvailabilityResponse r = availabilityService.getAvailability(propertyId,
                LocalDate.of(2030, 8, 1), LocalDate.of(2030, 8, 10));

        assertThat(r.available()).isFalse();
        assertThat(r.blockedRanges()).hasSize(1);
        assertThat(r.blockedRanges().get(0).checkIn()).isEqualTo(LocalDate.of(2030, 8, 3));
        assertThat(r.blockedRanges().get(0).checkOut()).isEqualTo(LocalDate.of(2030, 8, 5));
    }
}
