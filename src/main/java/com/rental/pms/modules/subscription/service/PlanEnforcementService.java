package com.rental.pms.modules.subscription.service;

import com.rental.pms.common.exception.TenantLimitExceededException;
import com.rental.pms.modules.subscription.entity.Subscription;
import com.rental.pms.modules.subscription.entity.SubscriptionPlan;
import com.rental.pms.modules.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Enforces subscription-plan limits on tenant resources.
 * Called by other modules (e.g., PropertyService) before creating a new resource.
 * Throws {@link TenantLimitExceededException} when the limit is reached.
 *
 * A {@code null} limit on the plan means unlimited (e.g., Agency plan).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanEnforcementService {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Verifies the tenant can create another property under its current plan.
     * @param tenantId tenant to check
     * @param currentCountSupplier lazy supplier — only evaluated for plans with a finite limit
     * @throws TenantLimitExceededException if current count >= plan's maxProperties
     */
    @Transactional(readOnly = true)
    public void checkPropertyLimit(UUID tenantId, LongSupplier currentCountSupplier) {
        SubscriptionPlan plan = getActivePlan(tenantId);
        if (plan.isUnlimitedProperties()) {
            return;
        }
        long current = currentCountSupplier.getAsLong();
        int max = plan.getMaxProperties();
        if (current >= max) {
            log.info("Property limit reached for tenant={}, plan={}, current={}, max={}",
                    tenantId, plan.getCode(), current, max);
            throw new TenantLimitExceededException("properties", (int) current, max);
        }
    }

    /**
     * Verifies the tenant can add another user under its current plan.
     * @param tenantId tenant to check
     * @param currentCountSupplier lazy supplier — only evaluated for plans with a finite limit
     * @throws TenantLimitExceededException if current count >= plan's maxUsers
     */
    @Transactional(readOnly = true)
    public void checkUserLimit(UUID tenantId, LongSupplier currentCountSupplier) {
        SubscriptionPlan plan = getActivePlan(tenantId);
        if (plan.isUnlimitedUsers()) {
            return;
        }
        long current = currentCountSupplier.getAsLong();
        int max = plan.getMaxUsers();
        if (current >= max) {
            log.info("User limit reached for tenant={}, plan={}, current={}, max={}",
                    tenantId, plan.getCode(), current, max);
            throw new TenantLimitExceededException("users", (int) current, max);
        }
    }

    private SubscriptionPlan getActivePlan(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findActiveByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "No active subscription found for tenant " + tenantId));
        return subscription.getPlan();
    }
}
