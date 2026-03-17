package com.rental.pms.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts between BIGINT minor units (e.g., cents/pence) and BigDecimal major units.
 * All monetary amounts are stored in the database as BIGINT (minor units)
 * to avoid floating-point precision issues.
 */
public final class MoneyUtil {

    private static final int MINOR_UNIT_SCALE = 2;
    private static final BigDecimal MINOR_UNIT_FACTOR = BigDecimal.valueOf(100);

    private MoneyUtil() {
        // Utility class — no instantiation
    }

    /**
     * Converts a BigDecimal major unit amount (e.g., 10.50) to BIGINT minor units (e.g., 1050).
     *
     * @param amount the amount in major units
     * @return the amount in minor units (cents/pence)
     * @throws IllegalArgumentException if amount is null or negative
     */
    public static long toMinorUnits(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must not be negative: " + amount);
        }
        return amount.multiply(MINOR_UNIT_FACTOR)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * Converts a BIGINT minor unit amount (e.g., 1050) to a BigDecimal major unit (e.g., 10.50).
     *
     * @param minorUnits the amount in minor units (cents/pence)
     * @return the amount in major units with 2 decimal places
     * @throws IllegalArgumentException if minorUnits is negative
     */
    public static BigDecimal toMajorUnits(long minorUnits) {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Minor units must not be negative: " + minorUnits);
        }
        return BigDecimal.valueOf(minorUnits)
                .divide(MINOR_UNIT_FACTOR, MINOR_UNIT_SCALE, RoundingMode.HALF_UP);
    }
}
