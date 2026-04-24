package com.rental.pms.modules.pricing.repository;

import com.rental.pms.modules.pricing.entity.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    /**
     * Active rules for a tenant that target either this specific property or all properties
     * (propertyId IS NULL), ordered by priority ascending (lower number applies first).
     */
    @Query("""
           SELECT r FROM PricingRule r
           WHERE r.tenantId = :tenantId
             AND r.active = true
             AND (r.propertyId = :propertyId OR r.propertyId IS NULL)
           ORDER BY r.priority ASC, r.createdAt ASC
           """)
    List<PricingRule> findActiveForProperty(UUID tenantId, UUID propertyId);
}
