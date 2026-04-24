package com.rental.pms.modules.pricing.service;

import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.pricing.dto.NightlyRateDto;
import com.rental.pms.modules.pricing.dto.PriceAdjustmentDto;
import com.rental.pms.modules.pricing.dto.PriceBreakdownResponse;
import com.rental.pms.modules.pricing.dto.PriceCalculationRequest;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pricing engine.
 * <p>
 * Flow:
 * <ol>
 *   <li>Load the property's base price.</li>
 *   <li>For each night in [checkIn, checkOut): pick the seasonal override whose range covers
 *       that night with the latest startDate; otherwise use base.</li>
 *   <li>Apply WEEKEND_SURCHARGE rules per-night (they modify individual nights, not the total).</li>
 *   <li>Sum to nightly subtotal.</li>
 *   <li>Apply remaining active rules in priority order (PERCENT = percent off the running total;
 *       FIXED = flat minor-units off). Stacking is sequential on the running total.</li>
 *   <li>Apply the coupon (same PERCENT vs FIXED semantics).</li>
 *   <li>Clamp to 0 and return the breakdown.</li>
 * </ol>
 * This service is pure computation — no writes, no locking. BookingService consumes the
 * result and persists it; callers hitting {@code /bookings/calculate-price} get a quote only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {

    private final PropertyRepository propertyRepository;
    private final SeasonalRateRepository seasonalRateRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final CouponRepository couponRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public PriceBreakdownResponse calculatePrice(PriceCalculationRequest request) {
        return calculate(
                currentUser.getTenantId(),
                request.propertyId(),
                request.checkIn(),
                request.checkOut(),
                request.couponCode());
    }

    /**
     * Entry point used by BookingService (avoids going through the request DTO).
     */
    public PriceBreakdownResponse calculate(UUID tenantId,
                                            UUID propertyId,
                                            LocalDate checkIn,
                                            LocalDate checkOut,
                                            String couponCode) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }

        Property property = propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));

        Long basePrice = property.getBasePricePerNightMinorUnits();
        if (basePrice == null) {
            throw new IllegalArgumentException(
                    "Property has no base price configured — cannot quote.");
        }

        List<SeasonalRate> overlapping = seasonalRateRepository.findOverlapping(
                tenantId, propertyId, checkIn, checkOut.minusDays(1));

        List<PricingRule> rules = pricingRuleRepository.findActiveForProperty(tenantId, propertyId);

        // Partition rules: per-night (WEEKEND_SURCHARGE) vs aggregate.
        List<PricingRule> perNightRules = new ArrayList<>();
        List<PricingRule> aggregateRules = new ArrayList<>();
        for (PricingRule r : rules) {
            if (r.getRuleType() == PricingRuleType.WEEKEND_SURCHARGE) {
                perNightRules.add(r);
            } else {
                aggregateRules.add(r);
            }
        }

        List<NightlyRateDto> nights = buildNightly(checkIn, checkOut, basePrice, overlapping, perNightRules);
        long nightlyTotal = nights.stream().mapToLong(NightlyRateDto::rateMinorUnits).sum();

        long running = nightlyTotal;
        List<PriceAdjustmentDto> adjustments = new ArrayList<>();
        long nights_count = ChronoUnit.DAYS.between(checkIn, checkOut);

        for (PricingRule rule : aggregateRules) {
            if (!ruleApplies(rule, checkIn, (int) nights_count)) continue;
            long delta = computeAdjustment(rule, running);
            if (delta == 0) continue;
            adjustments.add(new PriceAdjustmentDto(
                    "RULE:" + rule.getRuleType() + ":" + rule.getName(), delta));
            running -= delta;
        }

        if (couponCode != null && !couponCode.isBlank()) {
            Coupon coupon = resolveValidCoupon(tenantId, couponCode);
            long delta = computeCouponAdjustment(coupon, running);
            if (delta != 0) {
                adjustments.add(new PriceAdjustmentDto("COUPON:" + coupon.getCode(), delta));
                running -= delta;
            }
        }

        long total = Math.max(running, 0L);
        long discountTotal = adjustments.stream().mapToLong(PriceAdjustmentDto::amountMinorUnits).sum();

        String currency = property.getCurrency();
        return new PriceBreakdownResponse(nights, nightlyTotal, adjustments,
                discountTotal, total, currency);
    }

    // ---- nightly build ----

    private List<NightlyRateDto> buildNightly(LocalDate checkIn, LocalDate checkOut,
                                              long basePrice,
                                              List<SeasonalRate> seasonal,
                                              List<PricingRule> perNightRules) {
        List<NightlyRateDto> out = new ArrayList<>();
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            SeasonalRate winning = pickSeasonal(d, seasonal);
            long rate = winning != null ? winning.getPricePerNightMinorUnits() : basePrice;
            String name = winning != null ? "SEASONAL:" + winning.getName() : "BASE";

            for (PricingRule rule : perNightRules) {
                if (!weekendSurchargeApplies(rule, d)) continue;
                long delta = computeAdjustment(rule, rate);
                rate -= delta;                               // delta is positive for discount,
                name = name + "+" + rule.getName();          // negative for surcharge
            }
            out.add(new NightlyRateDto(d, rate, name));
        }
        return out;
    }

    private SeasonalRate pickSeasonal(LocalDate date, Collection<SeasonalRate> rates) {
        return rates.stream()
                .filter(r -> !date.isBefore(r.getStartDate()) && !date.isAfter(r.getEndDate()))
                .max(Comparator.comparing(SeasonalRate::getStartDate))
                .orElse(null);
    }

    // ---- rule applicability ----

    private boolean ruleApplies(PricingRule rule, LocalDate checkIn, int nights) {
        Map<String, Object> cond = rule.getConditions() == null ? new HashMap<>() : rule.getConditions();
        return switch (rule.getRuleType()) {
            case MIN_NIGHTS_DISCOUNT -> {
                Integer minNights = intFrom(cond, "minNights");
                yield minNights != null && nights >= minNights;
            }
            case LAST_MINUTE_DISCOUNT -> {
                Integer daysBefore = intFrom(cond, "daysBefore");
                if (daysBefore == null) yield false;
                long gap = ChronoUnit.DAYS.between(LocalDate.now(), checkIn);
                yield gap >= 0 && gap <= daysBefore;
            }
            case EARLY_BIRD_DISCOUNT -> {
                Integer daysBefore = intFrom(cond, "daysBefore");
                if (daysBefore == null) yield false;
                long gap = ChronoUnit.DAYS.between(LocalDate.now(), checkIn);
                yield gap >= daysBefore;
            }
            case WEEKEND_SURCHARGE -> false; // handled per-night
        };
    }

    @SuppressWarnings("unchecked")
    private boolean weekendSurchargeApplies(PricingRule rule, LocalDate date) {
        Map<String, Object> cond = rule.getConditions();
        if (cond == null) return false;
        Object raw = cond.get("nights");
        if (!(raw instanceof List<?> list)) return false;
        String dow = date.getDayOfWeek().name().substring(0, 3); // MON, TUE, ...
        for (Object o : list) {
            if (o != null && dow.equalsIgnoreCase(o.toString().substring(0, Math.min(3, o.toString().length())))) {
                return true;
            }
        }
        return false;
    }

    private Integer intFrom(Map<String, Object> cond, String key) {
        Object v = cond.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    // ---- adjustment math ----

    /** Returns a positive amount for a discount, negative for a surcharge. */
    private long computeAdjustment(PricingRule rule, long base) {
        if (rule.getAdjustmentType() == AdjustmentType.PERCENT) {
            BigDecimal pct = rule.getDiscountPercent() == null ? BigDecimal.ZERO : rule.getDiscountPercent();
            return pct.multiply(BigDecimal.valueOf(base))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValue();
        }
        return rule.getDiscountAmountMinorUnits() == null ? 0L : rule.getDiscountAmountMinorUnits();
    }

    // ---- coupon ----

    Coupon resolveValidCoupon(UUID tenantId, String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        Coupon coupon = couponRepository.findByTenantIdAndCode(tenantId, normalized)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found: " + code));

        if (!coupon.isActive()) {
            throw new IllegalArgumentException("Coupon is inactive: " + code);
        }
        Instant now = Instant.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            throw new IllegalArgumentException("Coupon not yet valid: " + code);
        }
        if (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil())) {
            throw new IllegalArgumentException("Coupon expired: " + code);
        }
        if (coupon.getMaxUses() != null && coupon.getCurrentUses() >= coupon.getMaxUses()) {
            throw new IllegalArgumentException("Coupon max uses reached: " + code);
        }
        return coupon;
    }

    private long computeCouponAdjustment(Coupon coupon, long base) {
        if (coupon.getDiscountType() == Coupon.DiscountType.PERCENTAGE) {
            BigDecimal pct = Optional.ofNullable(coupon.getDiscountPercent()).orElse(BigDecimal.ZERO);
            return pct.multiply(BigDecimal.valueOf(base))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValue();
        }
        return coupon.getDiscountValueMinorUnits() == null ? 0L : coupon.getDiscountValueMinorUnits();
    }

    /**
     * Bumps {@code currentUses} for the given coupon; called by BookingService once a booking
     * using the coupon is persisted. Kept here so pricing logic owns all coupon state.
     */
    @Transactional
    public void recordCouponUse(UUID tenantId, String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        couponRepository.findByTenantIdAndCode(tenantId, normalized).ifPresent(c -> {
            c.setCurrentUses(c.getCurrentUses() + 1);
            couponRepository.save(c);
        });
    }

}
