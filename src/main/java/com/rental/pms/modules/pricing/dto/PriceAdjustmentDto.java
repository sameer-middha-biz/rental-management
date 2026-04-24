package com.rental.pms.modules.pricing.dto;

/**
 * A single adjustment applied in the aggregate-rules / coupon phase.
 * Positive {@code amountMinorUnits} means a discount; negative means a surcharge.
 */
public record PriceAdjustmentDto(
        String source,             // e.g. "RULE:MIN_NIGHTS_DISCOUNT:7+ nights" or "COUPON:SUMMER"
        long amountMinorUnits
) {}
