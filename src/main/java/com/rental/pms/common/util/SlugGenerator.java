package com.rental.pms.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates URL-friendly slugs from arbitrary text.
 * Used for tenant slugs, property slugs, etc.
 */
public final class SlugGenerator {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final Pattern CONSECUTIVE_HYPHENS = Pattern.compile("-{2,}");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-|-$");

    private SlugGenerator() {
        // Utility class — no instantiation
    }

    /**
     * Converts the given text to a URL-friendly slug.
     * <ul>
     *   <li>Normalizes Unicode (NFD) and strips diacritics</li>
     *   <li>Converts to lowercase</li>
     *   <li>Replaces whitespace with hyphens</li>
     *   <li>Removes non-alphanumeric characters (except hyphens)</li>
     *   <li>Collapses consecutive hyphens</li>
     *   <li>Trims leading/trailing hyphens</li>
     * </ul>
     *
     * @param text the input text to slugify
     * @return a URL-friendly slug, or empty string if input is null/blank
     */
    public static String generateSlug(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        // Remove combining diacritical marks
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = normalized.toLowerCase(Locale.ROOT);
        slug = WHITESPACE.matcher(slug).replaceAll("-");
        slug = NON_LATIN.matcher(slug).replaceAll("");
        slug = CONSECUTIVE_HYPHENS.matcher(slug).replaceAll("-");
        slug = EDGE_HYPHENS.matcher(slug).replaceAll("");

        return slug;
    }
}
