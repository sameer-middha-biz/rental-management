package com.rental.pms.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    @Test
    void generateSlug_WithSimpleText_ShouldReturnLowercaseHyphenated() {
        assertThat(SlugGenerator.generateSlug("Sunny Beach House")).isEqualTo("sunny-beach-house");
    }

    @Test
    void generateSlug_WithSpecialCharacters_ShouldRemoveThem() {
        assertThat(SlugGenerator.generateSlug("Villa @#$% Sunrise!")).isEqualTo("villa-sunrise");
    }

    @Test
    void generateSlug_WithAccentedCharacters_ShouldStripDiacritics() {
        assertThat(SlugGenerator.generateSlug("Café Résumé")).isEqualTo("cafe-resume");
    }

    @Test
    void generateSlug_WithMultipleSpaces_ShouldCollapsToSingleHyphen() {
        assertThat(SlugGenerator.generateSlug("ocean   view   villa")).isEqualTo("ocean-view-villa");
    }

    @Test
    void generateSlug_WithLeadingAndTrailingSpaces_ShouldTrim() {
        assertThat(SlugGenerator.generateSlug("  coastal retreat  ")).isEqualTo("coastal-retreat");
    }

    @Test
    void generateSlug_WithConsecutiveHyphens_ShouldCollapseToOne() {
        assertThat(SlugGenerator.generateSlug("hello---world")).isEqualTo("hello-world");
    }

    @Test
    void generateSlug_WithNumbers_ShouldPreserveThem() {
        assertThat(SlugGenerator.generateSlug("Villa 42 Deluxe")).isEqualTo("villa-42-deluxe");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void generateSlug_WithNullOrBlankInput_ShouldReturnEmptyString(String input) {
        assertThat(SlugGenerator.generateSlug(input)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "'München Apartment', munchen-apartment",
            "'São Paulo Loft', sao-paulo-loft",
            "'Zürich Lake View', zurich-lake-view"
    })
    void generateSlug_WithInternationalCharacters_ShouldNormalize(String input, String expected) {
        assertThat(SlugGenerator.generateSlug(input)).isEqualTo(expected);
    }

    @Test
    void generateSlug_WithUnderscores_ShouldPreserveThem() {
        assertThat(SlugGenerator.generateSlug("hello_world")).isEqualTo("hello_world");
    }

    @Test
    void generateSlug_WithMixedCase_ShouldConvertToLowercase() {
        assertThat(SlugGenerator.generateSlug("MyAwesomeVilla")).isEqualTo("myawesomevilla");
    }
}
