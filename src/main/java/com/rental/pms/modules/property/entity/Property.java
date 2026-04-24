package com.rental.pms.modules.property.entity;

import com.rental.pms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Rental property / listing. Tenant-scoped.
 * Photos, amenities, tags, and group memberships live in separate tables
 * (see V3.3-V3.5) and are NOT joined here to keep the entity loadable without
 * N+1 surprises. Those relations are fetched explicitly where needed.
 */
@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, of = {})
public class Property extends BaseEntity {

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 200)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 50)
    private PropertyType propertyType;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_province", length = 100)
    private String stateProvince;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 3)
    private String country;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 7)
    private BigDecimal longitude;

    @Column(name = "max_guests")
    private Integer maxGuests;

    /** Base per-night rate in minor units (e.g. pence). Required for bookable properties. */
    @Column(name = "base_price_per_night_minor_units")
    private Long basePricePerNightMinorUnits;

    /** ISO 4217 currency code. Defaults to tenant currency if absent. */
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "bedrooms")
    private Integer bedrooms;

    @Column(name = "bathrooms")
    private Integer bathrooms;

    @Column(name = "beds")
    private Integer beds;

    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "default_housekeeper_id")
    private UUID defaultHousekeeperId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PropertyStatus status = PropertyStatus.ACTIVE;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
