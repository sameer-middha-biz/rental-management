package com.rental.pms.modules.pricing.entity;

/**
 * Supported pricing rule conditions. The {@code conditions} JSONB column carries
 * rule-specific parameters; see PricingService for the interpretation of each.
 */
public enum PricingRuleType {
    /** Applies when booking nights &gt;= conditions.minNights. */
    MIN_NIGHTS_DISCOUNT,
    /** Applies when (checkIn - today) &lt;= conditions.daysBefore. */
    LAST_MINUTE_DISCOUNT,
    /** Applies when (checkIn - today) &gt;= conditions.daysBefore. */
    EARLY_BIRD_DISCOUNT,
    /** Applies a per-night surcharge on weekdays listed in conditions.nights. */
    WEEKEND_SURCHARGE
}
