package com.rental.pms.modules.booking.entity;

import com.rental.pms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "booking_nightly_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, of = {})
public class BookingNightlyRate extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "rate_minor_units", nullable = false)
    private Long rateMinorUnits;

    @Column(name = "rate_name", nullable = false, length = 100)
    private String rateName;
}
