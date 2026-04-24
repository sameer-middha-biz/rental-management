package com.rental.pms.modules.pricing.entity;

public enum AdjustmentType {
    /** {@code discountPercent} is applied as a percentage of the running total. */
    PERCENT,
    /** {@code discountAmountMinorUnits} is subtracted (or added, if negative) from the total. */
    FIXED
}
