package com.rental.pms.modules.pricing.entity;

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

/**
 * Per-property seasonal price override. A night whose date falls in [startDate, endDate]
 * (inclusive) uses {@code pricePerNightMinorUnits} instead of the property's base price.
 * Overlapping ranges are resolved by picking the one with the latest startDate.
 */
@Entity
@Table(name = "seasonal_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, of = {})
public class SeasonalRate extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "price_per_night_minor_units", nullable = false)
    private Long pricePerNightMinorUnits;

    @Column(name = "min_stay")
    private Integer minStay;
}
