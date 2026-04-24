package com.rental.pms.modules.pricing.repository;

import com.rental.pms.modules.pricing.entity.SeasonalRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SeasonalRateRepository extends JpaRepository<SeasonalRate, UUID> {

    /**
     * Returns seasonal rates for a property whose range overlaps the given window.
     * Used by PricingService to build per-night overrides.
     */
    @Query("""
           SELECT s FROM SeasonalRate s
           WHERE s.tenantId = :tenantId
             AND s.propertyId = :propertyId
             AND s.startDate <= :windowEnd
             AND s.endDate >= :windowStart
           """)
    List<SeasonalRate> findOverlapping(UUID tenantId, UUID propertyId,
                                       LocalDate windowStart, LocalDate windowEnd);

    List<SeasonalRate> findByTenantIdAndPropertyIdOrderByStartDateAsc(UUID tenantId, UUID propertyId);
}
