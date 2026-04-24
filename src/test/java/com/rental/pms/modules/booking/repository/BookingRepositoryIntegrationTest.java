package com.rental.pms.modules.booking.repository;

import com.rental.pms.common.security.TenantContext;
import com.rental.pms.modules.booking.entity.Booking;
import com.rental.pms.modules.booking.entity.BookingStatus;
import com.rental.pms.modules.guest.entity.Guest;
import com.rental.pms.modules.guest.repository.GuestRepository;
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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("BookingRepository Integration Tests")
class BookingRepositoryIntegrationTest {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private GuestRepository guestRepository;
    @Autowired private TenantRepository tenantRepository;

    private Tenant tenant;
    private Property property;
    private Guest guest;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.saveAndFlush(Tenant.builder()
                .name("Agency X")
                .slug("agency-x-" + UUID.randomUUID().toString().substring(0, 8))
                .contactEmail("admin@x.test")
                .timezone("UTC").defaultCurrency("GBP")
                .status(TenantStatus.ACTIVE).build());
        TenantContext.setTenantId(tenant.getId());

        property = propertyRepository.saveAndFlush(Property.builder()
                .name("Seaside Cottage")
                .slug("seaside-" + UUID.randomUUID().toString().substring(0, 8))
                .propertyType(PropertyType.HOUSE)
                .status(PropertyStatus.ACTIVE)
                .basePricePerNightMinorUnits(10000L)
                .currency("GBP").build());

        guest = guestRepository.saveAndFlush(Guest.builder()
                .firstName("Jane").lastName("Doe").email("jane@x.com").build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Booking saveBooking(LocalDate ci, LocalDate co, BookingStatus status) {
        return bookingRepository.saveAndFlush(Booking.builder()
                .propertyId(property.getId())
                .guestId(guest.getId())
                .bookingReference("BK-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .checkIn(ci).checkOut(co)
                .status(status)
                .guestCount(2)
                .totalPriceMinorUnits(30000L)
                .currency("GBP").build());
    }

    @Test
    @DisplayName("existsOverlapping: exact match detected")
    void overlap_exact() {
        saveBooking(LocalDate.of(2030, 9, 1), LocalDate.of(2030, 9, 5), BookingStatus.CONFIRMED);

        assertThat(bookingRepository.existsOverlapping(tenant.getId(), property.getId(),
                LocalDate.of(2030, 9, 1), LocalDate.of(2030, 9, 5))).isTrue();
    }

    @Test
    @DisplayName("existsOverlapping: partial overlap detected")
    void overlap_partial() {
        saveBooking(LocalDate.of(2030, 9, 1), LocalDate.of(2030, 9, 5), BookingStatus.CONFIRMED);

        assertThat(bookingRepository.existsOverlapping(tenant.getId(), property.getId(),
                LocalDate.of(2030, 9, 3), LocalDate.of(2030, 9, 7))).isTrue();
    }

    @Test
    @DisplayName("existsOverlapping: adjacent (checkout = other checkin) does NOT overlap")
    void overlap_adjacent() {
        saveBooking(LocalDate.of(2030, 9, 1), LocalDate.of(2030, 9, 5), BookingStatus.CONFIRMED);

        assertThat(bookingRepository.existsOverlapping(tenant.getId(), property.getId(),
                LocalDate.of(2030, 9, 5), LocalDate.of(2030, 9, 10))).isFalse();
        assertThat(bookingRepository.existsOverlapping(tenant.getId(), property.getId(),
                LocalDate.of(2030, 8, 28), LocalDate.of(2030, 9, 1))).isFalse();
    }

    @Test
    @DisplayName("existsOverlapping: CANCELLED and DECLINED bookings do not block")
    void overlap_ignoresTerminalStatuses() {
        saveBooking(LocalDate.of(2030, 9, 1), LocalDate.of(2030, 9, 5), BookingStatus.CANCELLED);
        saveBooking(LocalDate.of(2030, 9, 1), LocalDate.of(2030, 9, 5), BookingStatus.DECLINED);

        assertThat(bookingRepository.existsOverlapping(tenant.getId(), property.getId(),
                LocalDate.of(2030, 9, 2), LocalDate.of(2030, 9, 4))).isFalse();
    }

    @Test
    @DisplayName("existsOverlapping: tenant isolation — other-tenant bookings do not block")
    void overlap_tenantIsolation() {
        // Seed a second tenant with an overlapping booking on a different property+guest.
        Tenant other = tenantRepository.saveAndFlush(Tenant.builder()
                .name("Other").slug("other-" + UUID.randomUUID().toString().substring(0, 8))
                .contactEmail("a@o.test").timezone("UTC").defaultCurrency("GBP")
                .status(TenantStatus.ACTIVE).build());

        TenantContext.setTenantId(other.getId());
        Property otherProp = propertyRepository.saveAndFlush(Property.builder()
                .name("Other Villa")
                .slug("other-villa-" + UUID.randomUUID().toString().substring(0, 8))
                .propertyType(PropertyType.HOUSE).status(PropertyStatus.ACTIVE)
                .basePricePerNightMinorUnits(5000L).currency("GBP").build());
        Guest otherGuest = guestRepository.saveAndFlush(Guest.builder()
                .firstName("O").lastName("O").build());
        bookingRepository.saveAndFlush(Booking.builder()
                .propertyId(otherProp.getId()).guestId(otherGuest.getId())
                .bookingReference("BK-O" + UUID.randomUUID().toString().substring(0, 5).toUpperCase())
                .checkIn(LocalDate.of(2030, 9, 1)).checkOut(LocalDate.of(2030, 9, 5))
                .status(BookingStatus.CONFIRMED)
                .guestCount(1).totalPriceMinorUnits(5000L).currency("GBP").build());

        TenantContext.setTenantId(tenant.getId());
        assertThat(bookingRepository.existsOverlapping(tenant.getId(), property.getId(),
                LocalDate.of(2030, 9, 1), LocalDate.of(2030, 9, 5))).isFalse();
    }

    @Test
    @DisplayName("findOverlapping returns the blocking bookings")
    void findOverlapping_returnsRecords() {
        Booking b = saveBooking(LocalDate.of(2030, 9, 1), LocalDate.of(2030, 9, 5), BookingStatus.CONFIRMED);

        var hits = bookingRepository.findOverlapping(tenant.getId(), property.getId(),
                LocalDate.of(2030, 9, 3), LocalDate.of(2030, 9, 10));

        assertThat(hits).extracting(Booking::getId).containsExactly(b.getId());
    }
}
