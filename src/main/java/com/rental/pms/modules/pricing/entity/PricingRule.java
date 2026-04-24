package com.rental.pms.modules.pricing.entity;

import com.rental.pms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Declarative pricing adjustment applied after seasonal rates.
 * {@code propertyId} may be null, meaning the rule applies to every property in the tenant.
 * {@code conditions} is rule-type specific (see {@link PricingRuleType}).
 */
@Entity
@Table(name = "pricing_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, of = {})
public class PricingRule extends BaseEntity {

    @Column(name = "property_id")
    private UUID propertyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 40)
    private PricingRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 10)
    private AdjustmentType adjustmentType;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "discount_amount_minor_units")
    private Long discountAmountMinorUnits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> conditions = new HashMap<>();

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
