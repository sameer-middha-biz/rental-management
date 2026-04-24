package com.rental.pms.modules.pricing.dto;

import java.util.List;

public record PriceBreakdownResponse(
        List<NightlyRateDto> nightlyRates,
        long nightlyTotalMinorUnits,
        List<PriceAdjustmentDto> adjustments,
        long discountTotalMinorUnits,
        long totalMinorUnits,
        String currency
) {}
