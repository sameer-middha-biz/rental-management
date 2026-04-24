package com.rental.pms.modules.booking.service;

import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.security.JwtUserDetails;
import com.rental.pms.common.security.TenantContext;
import com.rental.pms.modules.booking.dto.CreateBookingRequest;
import com.rental.pms.modules.booking.entity.BookingStatus;
import com.rental.pms.modules.booking.repository.BookingNightlyRateRepository;
import com.rental.pms.modules.booking.repository.BookingRepository;
import com.rental.pms.modules.guest.entity.Guest;
import com.rental.pms.modules.guest.repository.GuestRepository;
import com.rental.pms.modules.pricing.entity.Coupon;
import com.rental.pms.modules.pricing.repository.CouponRepository;
import com.rental.pms.modules.property.entity.Property;
import com.rental.pms.modules.property.entity.PropertyStatus;
import com.rental.pms.modules.property.entity.PropertyType;
import com.rental.pms.modules.property.repository.PropertyRepository;
import com.rental.pms.modules.tenant.entity.Tenant;
import com.rental.pms.modules.tenant.entity.TenantStatus;
import com.rental.pms.modules.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end test for the booking flow.
 * <p>
 * Exercises the full stack with a real (Testcontainers) Postgres: Flyway migrations,
 * advisory locking, pricing engine, entity persistence, event publication. Focus is on
 * the invariants that are most likely to silently break in refactors: double-booking
 * rejection and price/total persistence.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Booking end-to-end integration")
class BookingServiceIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingNightlyRateRepository nightlyRateRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private GuestRepository guestRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private CouponRepository couponRepository;

    private Tenant tenant;
    private Property property;
    private Guest guest;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.saveAndFlush(Tenant.builder()
                .name("E2E Agency")
                .slug("e2e-" + UUID.randomUUID().toString().substring(0, 8))
                .contactEmail("e2e@test.local")
                .timezone("UTC").defaultCurrency("GBP")
                .status(TenantStatus.ACTIVE).build());

        authenticateAs(tenant.getId());

        property = propertyRepository.saveAndFlush(Property.builder()
                .name("Beach House")
                .slug("beach-" + UUID.randomUUID().toString().substring(0, 8))
                .propertyType(PropertyType.HOUSE)
                .status(PropertyStatus.ACTIVE)
                .basePricePerNightMinorUnits(10000L)     // £100/night
                .currency("GBP").build());

        guest = guestRepository.saveAndFlush(Guest.builder()
                .firstName("E2E").lastName("Guest").email("e2e-g@test.local").build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticateAs(UUID tenantId) {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), tenantId,
                List.of("AGENCY_ADMIN"),
                List.of("BOOKING_CREATE", "BOOKING_VIEW", "BOOKING_EDIT"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
        TenantContext.setTenantId(tenantId);
    }

    @Test
    @DisplayName("create: persists booking + nightly rates with total from pricing engine")
    void create_happyPath_persistsEverything() {
        CreateBookingRequest req = new CreateBookingRequest(
                property.getId(), guest.getId(),
                LocalDate.of(2030, 8, 1), LocalDate.of(2030, 8, 4),
                2, null, null, null, null);

        var resp = bookingService.create(req);

        assertThat(resp.id()).isNotNull();
        assertThat(resp.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(resp.totalPriceMinorUnits()).isEqualTo(30000L); // 3 nights × £100
        assertThat(resp.currency()).isEqualTo("GBP");

        var rates = nightlyRateRepository.findByBookingIdOrderByDateAsc(resp.id());
        assertThat(rates).hasSize(3);
        assertThat(rates).allSatisfy(r -> assertThat(r.getRateMinorUnits()).isEqualTo(10000L));
    }

    @Test
    @DisplayName("create: second booking overlapping the first → BOOKING.AVAILABILITY.CONFLICT")
    void create_overlapRejected() {
        CreateBookingRequest first = new CreateBookingRequest(
                property.getId(), guest.getId(),
                LocalDate.of(2030, 8, 10), LocalDate.of(2030, 8, 15),
                2, null, null, null, null);
        bookingService.create(first);

        CreateBookingRequest overlapping = new CreateBookingRequest(
                property.getId(), guest.getId(),
                LocalDate.of(2030, 8, 12), LocalDate.of(2030, 8, 18),
                2, null, null, null, null);

        assertThatThrownBy(() -> bookingService.create(overlapping))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("BOOKING.AVAILABILITY.CONFLICT");

        // only the first booking is persisted
        assertThat(bookingRepository.findByTenantId(tenant.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .hasSize(1);
    }

    @Test
    @DisplayName("create: adjacent dates (checkout = other checkin) are allowed")
    void create_adjacentAllowed() {
        bookingService.create(new CreateBookingRequest(
                property.getId(), guest.getId(),
                LocalDate.of(2030, 9, 1), LocalDate.of(2030, 9, 5),
                2, null, null, null, null));

        // exact turn-over day: check-out 9/5 == check-in 9/5 → should succeed
        bookingService.create(new CreateBookingRequest(
                property.getId(), guest.getId(),
                LocalDate.of(2030, 9, 5), LocalDate.of(2030, 9, 10),
                2, null, null, null, null));

        assertThat(bookingRepository.findByTenantId(tenant.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .hasSize(2);
    }

    @Test
    @DisplayName("create with coupon: discount applied, coupon use count incremented")
    void create_withCoupon_appliesDiscountAndIncrements() {
        Coupon c = couponRepository.saveAndFlush(Coupon.builder()
                .code("SAVE20")
                .discountType(Coupon.DiscountType.PERCENTAGE)
                .discountPercent(new BigDecimal("20.00"))
                .active(true).currentUses(0).build());

        var resp = bookingService.create(new CreateBookingRequest(
                property.getId(), guest.getId(),
                LocalDate.of(2030, 10, 1), LocalDate.of(2030, 10, 4),
                2, null, "save20", null, null));

        // 3 × £100 = £300, minus 20% = £240
        assertThat(resp.totalPriceMinorUnits()).isEqualTo(24000L);
        assertThat(resp.couponCode()).isEqualTo("SAVE20");

        Coupon reloaded = couponRepository.findById(c.getId()).orElseThrow();
        assertThat(reloaded.getCurrentUses()).isEqualTo(1);
    }
}
