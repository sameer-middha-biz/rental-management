package com.rental.pms.modules.pricing.dto;

import java.time.LocalDate;

/**
 * Per-night rate after seasonal override and weekend surcharge, before aggregate rules / coupon.
 * {@code rateName} identifies the source: "BASE", "SEASONAL:{name}", or adds "+WEEKEND_SURCHARGE".
 */
public record NightlyRateDto(
        LocalDate date,
        long rateMinorUnits,
        String rateName
) {}
