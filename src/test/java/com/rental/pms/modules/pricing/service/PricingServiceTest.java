package com.rental.pms.modules.pricing.service;

import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.pricing.dto.PriceBreakdownResponse;
import com.rental.pms.modules.pricing.entity.AdjustmentType;
import com.rental.pms.modules.pricing.entity.Coupon;
import com.rental.pms.modules.pricing.entity.PricingRule;
import com.rental.pms.modules.pricing.entity.PricingRuleType;
import com.rental.pms.modules.pricing.entity.SeasonalRate;
import com.rental.pms.modules.pricing.repository.CouponRepository;
import com.rental.pms.modules.pricing.repository.PricingRuleRepository;
import com.rental.pms.modules.pricing.repository.SeasonalRateRepository;
import com.rental.pms.modules.property.entity.Property;
import com.rental.pms.modules.property.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PricingService Tests")
class PricingServiceTest {

    @Mock private PropertyRepository propertyRepository;
    @Mock private SeasonalRateRepository seasonalRateRepository;
    @Mock private PricingRuleRepository pricingRuleRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private CurrentUser currentUser;

    @InjectMocks private PricingService pricingService;

    private UUID tenantId;
    private UUID propertyId;
    private Property property;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
        property = Property.builder()
                .basePricePerNightMinorUnits(10000L)   // £100/night
                .currency("GBP")
                .build();
        given(propertyRepository.findByIdAndTenantId(propertyId, tenantId))
                .willReturn(Optional.of(property));
        given(seasonalRateRepository.findOverlapping(eq(tenantId), eq(propertyId), any(), any()))
                .willReturn(List.of());
        given(pricingRuleRepository.findActiveForProperty(tenantId, propertyId))
                .willReturn(List.of());
    }

    @Test
    @DisplayName("base price only: 3 nights at £100 = £300, no adjustments")
    void basePriceOnly() {
        PriceBreakdownResponse r = pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 4), null);

        assertThat(r.nightlyRates()).hasSize(3);
        assertThat(r.nightlyRates()).allMatch(n -> n.rateMinorUnits() == 10000L);
        assertThat(r.nightlyRates()).allMatch(n -> n.rateName().equals("BASE"));
        assertThat(r.nightlyTotalMinorUnits()).isEqualTo(30000L);
        assertThat(r.adjustments()).isEmpty();
        assertThat(r.totalMinorUnits()).isEqualTo(30000L);
        assertThat(r.currency()).isEqualTo("GBP");
    }

    @Test
    @DisplayName("seasonal rate overrides base for overlapping nights")
    void seasonalOverride() {
        SeasonalRate summer = SeasonalRate.builder()
                .propertyId(propertyId)
                .name("Summer")
                .startDate(LocalDate.of(2030, 6, 2))
                .endDate(LocalDate.of(2030, 6, 3))
                .pricePerNightMinorUnits(20000L) // £200/night in summer
                .build();
        given(seasonalRateRepository.findOverlapping(eq(tenantId), eq(propertyId), any(), any()))
                .willReturn(List.of(summer));

        PriceBreakdownResponse r = pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 4), null);

        // night 1 = base (100), nights 2+3 = summer (200 each) → 500
        assertThat(r.nightlyTotalMinorUnits()).isEqualTo(50000L);
        assertThat(r.nightlyRates().get(0).rateName()).isEqualTo("BASE");
        assertThat(r.nightlyRates().get(1).rateName()).isEqualTo("SEASONAL:Summer");
        assertThat(r.nightlyRates().get(2).rateName()).isEqualTo("SEASONAL:Summer");
    }

    @Test
    @DisplayName("overlapping seasonal: later startDate wins")
    void overlappingSeasonalLaterWins() {
        SeasonalRate wide = SeasonalRate.builder()
                .propertyId(propertyId).name("Wide")
                .startDate(LocalDate.of(2030, 6, 1)).endDate(LocalDate.of(2030, 6, 10))
                .pricePerNightMinorUnits(15000L).build();
        SeasonalRate narrower = SeasonalRate.builder()
                .propertyId(propertyId).name("Narrower")
                .startDate(LocalDate.of(2030, 6, 3)).endDate(LocalDate.of(2030, 6, 5))
                .pricePerNightMinorUnits(25000L).build();
        given(seasonalRateRepository.findOverlapping(eq(tenantId), eq(propertyId), any(), any()))
                .willReturn(List.of(wide, narrower));

        PriceBreakdownResponse r = pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 2), LocalDate.of(2030, 6, 5), null);

        // 6/2 wide=150, 6/3 narrower=250 (later startDate wins), 6/4 narrower=250
        assertThat(r.nightlyRates().get(0).rateMinorUnits()).isEqualTo(15000L);
        assertThat(r.nightlyRates().get(1).rateMinorUnits()).isEqualTo(25000L);
        assertThat(r.nightlyRates().get(2).rateMinorUnits()).isEqualTo(25000L);
    }

    @Test
    @DisplayName("MIN_NIGHTS_DISCOUNT: 7+ nights gets 10% off total")
    void minNightsDiscountApplied() {
        Map<String, Object> cond = new HashMap<>();
        cond.put("minNights", 7);
        PricingRule rule = PricingRule.builder()
                .propertyId(propertyId)
                .name("Weekly")
                .ruleType(PricingRuleType.MIN_NIGHTS_DISCOUNT)
                .adjustmentType(AdjustmentType.PERCENT)
                .discountPercent(new BigDecimal("10.00"))
                .priority(100).active(true).conditions(cond).build();
        given(pricingRuleRepository.findActiveForProperty(tenantId, propertyId))
                .willReturn(List.of(rule));

        PriceBreakdownResponse r = pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 8), null); // 7 nights

        assertThat(r.nightlyTotalMinorUnits()).isEqualTo(70000L);
        assertThat(r.adjustments()).hasSize(1);
        assertThat(r.adjustments().get(0).amountMinorUnits()).isEqualTo(7000L); // 10% of 70000
        assertThat(r.totalMinorUnits()).isEqualTo(63000L);
    }

    @Test
    @DisplayName("MIN_NIGHTS_DISCOUNT: does not apply when below threshold")
    void minNightsDiscountNotApplied() {
        Map<String, Object> cond = new HashMap<>();
        cond.put("minNights", 7);
        PricingRule rule = PricingRule.builder()
                .propertyId(propertyId).name("Weekly")
                .ruleType(PricingRuleType.MIN_NIGHTS_DISCOUNT)
                .adjustmentType(AdjustmentType.PERCENT)
                .discountPercent(new BigDecimal("10.00"))
                .priority(100).active(true).conditions(cond).build();
        given(pricingRuleRepository.findActiveForProperty(tenantId, propertyId))
                .willReturn(List.of(rule));

        PriceBreakdownResponse r = pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 4), null);

        assertThat(r.adjustments()).isEmpty();
        assertThat(r.totalMinorUnits()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("FIXED coupon subtracts flat minor units from total")
    void fixedCouponApplied() {
        Coupon c = Coupon.builder()
                .code("SAVE10").discountType(Coupon.DiscountType.FIXED)
                .discountValueMinorUnits(1000L).active(true).currentUses(0).build();
        given(couponRepository.findByTenantIdAndCode(tenantId, "SAVE10")).willReturn(Optional.of(c));

        PriceBreakdownResponse r = pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 4), "save10");

        assertThat(r.nightlyTotalMinorUnits()).isEqualTo(30000L);
        assertThat(r.adjustments()).hasSize(1);
        assertThat(r.adjustments().get(0).source()).isEqualTo("COUPON:SAVE10");
        assertThat(r.adjustments().get(0).amountMinorUnits()).isEqualTo(1000L);
        assertThat(r.totalMinorUnits()).isEqualTo(29000L);
    }

    @Test
    @DisplayName("PERCENTAGE coupon applies to running total (after rules)")
    void percentCouponStacksAfterRule() {
        Map<String, Object> cond = new HashMap<>();
        cond.put("minNights", 3);
        PricingRule rule = PricingRule.builder()
                .propertyId(propertyId).name("Stay3")
                .ruleType(PricingRuleType.MIN_NIGHTS_DISCOUNT)
                .adjustmentType(AdjustmentType.PERCENT)
                .discountPercent(new BigDecimal("10.00"))
                .priority(100).active(true).conditions(cond).build();
        given(pricingRuleRepository.findActiveForProperty(tenantId, propertyId))
                .willReturn(List.of(rule));

        Coupon c = Coupon.builder()
                .code("VIP").discountType(Coupon.DiscountType.PERCENTAGE)
                .discountPercent(new BigDecimal("5.00")).active(true).currentUses(0).build();
        given(couponRepository.findByTenantIdAndCode(tenantId, "VIP")).willReturn(Optional.of(c));

        PriceBreakdownResponse r = pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 4), "VIP");

        // 30000 - 10% = 27000; then - 5% = 25650
        assertThat(r.totalMinorUnits()).isEqualTo(25650L);
        assertThat(r.adjustments()).hasSize(2);
    }

    @Test
    @DisplayName("expired coupon → 400")
    void expiredCouponRejected() {
        Coupon c = Coupon.builder()
                .code("OLD").discountType(Coupon.DiscountType.FIXED)
                .discountValueMinorUnits(500L).active(true).currentUses(0)
                .validUntil(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        given(couponRepository.findByTenantIdAndCode(tenantId, "OLD")).willReturn(Optional.of(c));

        assertThatThrownBy(() -> pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 4), "OLD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("max-used coupon → 400")
    void maxUsesCouponRejected() {
        Coupon c = Coupon.builder()
                .code("ONCE").discountType(Coupon.DiscountType.FIXED)
                .discountValueMinorUnits(500L).active(true).maxUses(1).currentUses(1).build();
        given(couponRepository.findByTenantIdAndCode(tenantId, "ONCE")).willReturn(Optional.of(c));

        assertThatThrownBy(() -> pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 4), "ONCE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max uses");
    }

    @Test
    @DisplayName("unknown coupon → 400")
    void unknownCouponRejected() {
        given(couponRepository.findByTenantIdAndCode(tenantId, "NOPE"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 4), "nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Coupon not found");
    }

    @Test
    @DisplayName("checkOut must be after checkIn")
    void invalidDatesRejected() {
        assertThatThrownBy(() -> pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 4), LocalDate.of(2030, 6, 4), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("property without base price rejected")
    void missingBasePriceRejected() {
        property.setBasePricePerNightMinorUnits(null);

        assertThatThrownBy(() -> pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 4), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no base price");
    }

    @Test
    @DisplayName("total clamps to 0 when coupon exceeds subtotal")
    void totalClampsToZero() {
        Coupon c = Coupon.builder()
                .code("HUGE").discountType(Coupon.DiscountType.FIXED)
                .discountValueMinorUnits(999999L).active(true).currentUses(0).build();
        given(couponRepository.findByTenantIdAndCode(tenantId, "HUGE")).willReturn(Optional.of(c));

        PriceBreakdownResponse r = pricingService.calculate(
                tenantId, propertyId,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 4), "HUGE");

        assertThat(r.totalMinorUnits()).isZero();
    }
}
