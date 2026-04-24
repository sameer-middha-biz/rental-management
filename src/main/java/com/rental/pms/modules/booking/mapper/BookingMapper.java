package com.rental.pms.modules.booking.mapper;

import com.rental.pms.modules.booking.dto.BookingResponse;
import com.rental.pms.modules.booking.dto.UpdateBookingRequest;
import com.rental.pms.modules.booking.entity.Booking;
import com.rental.pms.modules.booking.entity.BookingNightlyRate;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * disableBuilder because Booking extends BaseEntity (BaseEntity fields aren't in Lombok's
 * generated builder — MapStruct then tries to assign them through setters, which is fine).
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface BookingMapper {

    @Mapping(target = "nightlyRates", ignore = true)
    BookingResponse toResponse(Booking booking);

    default BookingResponse toResponse(Booking booking, List<BookingNightlyRate> rates) {
        BookingResponse base = toResponse(booking);
        List<BookingResponse.NightlyRate> nr = rates.stream()
                .map(r -> new BookingResponse.NightlyRate(r.getDate(), r.getRateMinorUnits(), r.getRateName()))
                .toList();
        return new BookingResponse(
                base.id(), base.tenantId(), base.propertyId(), base.guestId(),
                base.bookingReference(), base.checkIn(), base.checkOut(),
                base.status(), base.guestCount(), base.totalPriceMinorUnits(),
                base.currency(), base.source(), base.couponCode(),
                base.specialRequests(), base.notes(),
                base.cancelledAt(), base.cancelledReason(),
                nr, base.createdAt(), base.updatedAt());
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void applyUpdate(UpdateBookingRequest request, @MappingTarget Booking target);

    @Named("nightlyRateToDto")
    default BookingResponse.NightlyRate nightlyRateToDto(BookingNightlyRate r) {
        return new BookingResponse.NightlyRate(r.getDate(), r.getRateMinorUnits(), r.getRateName());
    }
}
