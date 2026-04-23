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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionPlanRepository planRepository;

    @Mock
    private SubscriptionMapper subscriptionMapper;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private SubscriptionPlan starterPlan;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        starterPlan = SubscriptionPlan.builder()
                .id(UUID.fromString("20000000-0000-0000-0000-000000000001"))
                .code("STARTER")
                .name("Starter")
                .maxProperties(5)
                .maxUsers(5)
                .monthlyPriceMinorUnits(0L)
                .currency("GBP")
                .features(new HashMap<>())
                .isActive(true)
                .sortOrder(1)
                .build();
    }

    @Test
    void createStarterSubscription_ShouldPersistActiveSubscription() {
        when(planRepository.findByCode("STARTER")).thenReturn(Optional.of(starterPlan));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.createStarterSubscription(tenantId);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();

        assertThat(saved.getPlan()).isEqualTo(starterPlan);
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getStartDate()).isNotNull();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void createStarterSubscription_WhenPlanMissing_ShouldThrow() {
        when(planRepository.findByCode("STARTER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.createStarterSubscription(tenantId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Starter plan not seeded");

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void getCurrentSubscription_WhenFound_ShouldReturnResponse() {
        Subscription subscription = Subscription.builder()
                .plan(starterPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now())
                .build();
        SubscriptionResponse response = new SubscriptionResponse(
                UUID.randomUUID(), tenantId,
                new SubscriptionPlanResponse(starterPlan.getId(), "STARTER", "Starter",
                        null, 5, 5, 0L, "GBP", new HashMap<>(), 1),
                "ACTIVE", Instant.now(), null, null, null, null, null, Instant.now());

        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(subscriptionRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.of(subscription));
        when(subscriptionMapper.toResponse(subscription)).thenReturn(response);

        SubscriptionResponse result = subscriptionService.getCurrentSubscription();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.plan().code()).isEqualTo("STARTER");
    }

    @Test
    void getCurrentSubscription_WhenMissing_ShouldThrowResourceNotFound() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(subscriptionRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getCurrentSubscription())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPlans_ShouldReturnOnlyActivePlansSorted() {
        SubscriptionPlan pro = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .code("PRO")
                .name("Pro")
                .maxProperties(25)
                .maxUsers(20)
                .monthlyPriceMinorUnits(4900L)
                .currency("GBP")
                .features(new HashMap<>())
                .isActive(true)
                .sortOrder(2)
                .build();

        when(planRepository.findAllByIsActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(starterPlan, pro));
        when(subscriptionMapper.toPlanResponse(starterPlan)).thenReturn(
                new SubscriptionPlanResponse(starterPlan.getId(), "STARTER", "Starter",
                        null, 5, 5, 0L, "GBP", new HashMap<>(), 1));
        when(subscriptionMapper.toPlanResponse(pro)).thenReturn(
                new SubscriptionPlanResponse(pro.getId(), "PRO", "Pro",
                        null, 25, 20, 4900L, "GBP", new HashMap<>(), 2));

        List<SubscriptionPlanResponse> plans = subscriptionService.getPlans();

        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).code()).isEqualTo("STARTER");
        assertThat(plans.get(1).code()).isEqualTo("PRO");
    }
}
