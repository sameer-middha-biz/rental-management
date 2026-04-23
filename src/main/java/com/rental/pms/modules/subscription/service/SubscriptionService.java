package com.rental.pms.modules.subscription.service;

import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.subscription.dto.SubscriptionPlanResponse;
import com.rental.pms.modules.subscription.dto.SubscriptionResponse;
import com.rental.pms.modules.subscription.entity.Subscription;
import com.rental.pms.modules.subscription.entity.SubscriptionPlan;
import com.rental.pms.modules.subscription.entity.SubscriptionStatus;
import com.rental.pms.modules.subscription.mapper.SubscriptionMapper;
import com.rental.pms.modules.subscription.repository.SubscriptionPlanRepository;
import com.rental.pms.modules.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages tenant subscriptions. Phase 4 scope: plan lookup, starter subscription
 * provisioning on tenant registration, and current-subscription queries. Stripe
 * integration (upgrade/downgrade/cancel) is out of scope for Phase 4.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    /** Default plan assigned to new tenants on registration. */
    public static final String DEFAULT_PLAN_CODE = "STARTER";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final CurrentUser currentUser;

    /**
     * Creates the free Starter subscription for a newly registered tenant.
     * Invoked from {@code TenantRegistrationService.register()} inside the
     * registration transaction.
     */
    @Transactional
    public void createStarterSubscription(UUID tenantId) {
        SubscriptionPlan starter = planRepository.findByCode(DEFAULT_PLAN_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Starter plan not seeded. Check V3.6__seed_subscription_plans.sql"));

        Subscription subscription = Subscription.builder()
                .plan(starter)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now())
                .build();
        // tenantId is populated by TenantInterceptor from TenantContext;
        // TenantRegistrationService sets the context before calling us.
        subscription = subscriptionRepository.save(subscription);

        log.info("Created STARTER subscription for tenant={}, subscriptionId={}",
                tenantId, subscription.getId());
    }

    /**
     * Returns the current tenant's active (or most recent) subscription.
     */
    public SubscriptionResponse getCurrentSubscription() {
        UUID tenantId = currentUser.getTenantId();
        Subscription subscription = subscriptionRepository.findActiveByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription", "tenantId", tenantId));
        return subscriptionMapper.toResponse(subscription);
    }

    /**
     * Returns all active plans for public display (e.g., upgrade page).
     */
    public List<SubscriptionPlanResponse> getPlans() {
        return planRepository.findAllByIsActiveTrueOrderBySortOrderAsc().stream()
                .map(subscriptionMapper::toPlanResponse)
                .toList();
    }
}
