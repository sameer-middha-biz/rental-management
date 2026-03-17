package com.rental.pms.common.util;

/**
 * Shared application constants.
 * Values are intentionally not configurable — they represent
 * business-invariant defaults used across modules.
 */
public final class Constants {

    /** Default page size for paginated API responses */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Maximum allowed page size to prevent excessive queries */
    public static final int MAX_PAGE_SIZE = 100;

    private Constants() {
        // Utility class — no instantiation
    }
}
