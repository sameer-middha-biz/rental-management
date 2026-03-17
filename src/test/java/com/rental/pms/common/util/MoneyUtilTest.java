package com.rental.pms.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyUtilTest {

    @Test
    void toMinorUnits_WithWholeAmount_ShouldConvertCorrectly() {
        assertThat(MoneyUtil.toMinorUnits(new BigDecimal("10.00"))).isEqualTo(1000L);
    }

    @Test
    void toMinorUnits_WithFractionalAmount_ShouldConvertCorrectly() {
        assertThat(MoneyUtil.toMinorUnits(new BigDecimal("10.50"))).isEqualTo(1050L);
    }

    @Test
    void toMinorUnits_WithZero_ShouldReturnZero() {
        assertThat(MoneyUtil.toMinorUnits(BigDecimal.ZERO)).isEqualTo(0L);
    }

    @Test
    void toMinorUnits_WithSubCentAmount_ShouldRoundHalfUp() {
        assertThat(MoneyUtil.toMinorUnits(new BigDecimal("10.555"))).isEqualTo(1056L);
    }

    @Test
    void toMinorUnits_WithNull_ShouldThrowIllegalArgument() {
        assertThatThrownBy(() -> MoneyUtil.toMinorUnits(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void toMinorUnits_WithNegativeAmount_ShouldThrowIllegalArgument() {
        assertThatThrownBy(() -> MoneyUtil.toMinorUnits(new BigDecimal("-5.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void toMajorUnits_WithWholeMinorUnits_ShouldConvertCorrectly() {
        assertThat(MoneyUtil.toMajorUnits(1000L)).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void toMajorUnits_WithFractionalMinorUnits_ShouldConvertCorrectly() {
        assertThat(MoneyUtil.toMajorUnits(1050L)).isEqualTo(new BigDecimal("10.50"));
    }

    @Test
    void toMajorUnits_WithZero_ShouldReturnZero() {
        assertThat(MoneyUtil.toMajorUnits(0L)).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void toMajorUnits_WithSingleCent_ShouldConvertCorrectly() {
        assertThat(MoneyUtil.toMajorUnits(1L)).isEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void toMajorUnits_WithNegative_ShouldThrowIllegalArgument() {
        assertThatThrownBy(() -> MoneyUtil.toMajorUnits(-100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void roundTrip_ShouldPreserveValue() {
        BigDecimal original = new BigDecimal("99.99");
        long minorUnits = MoneyUtil.toMinorUnits(original);
        BigDecimal converted = MoneyUtil.toMajorUnits(minorUnits);

        assertThat(converted).isEqualByComparingTo(original);
    }

    @Test
    void toMajorUnits_WithLargeAmount_ShouldConvertCorrectly() {
        assertThat(MoneyUtil.toMajorUnits(999999999L)).isEqualTo(new BigDecimal("9999999.99"));
    }
}
