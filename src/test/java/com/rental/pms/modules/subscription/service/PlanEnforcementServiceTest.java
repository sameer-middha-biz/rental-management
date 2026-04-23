package com.rental.pms.modules.subscription.service;

import com.rental.pms.common.exception.TenantLimitExceededException;
import com.rental.pms.modules.subscription.entity.Subscription;
import com.rental.pms.modules.subscription.entity.SubscriptionPlan;
import com.rental.pms.modules.subscription.entity.SubscriptionStatus;
import com.rental.pms.modules.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanEnforcementServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private PlanEnforcementService planEnforcementService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    private void stubActiveSubscription(Integer maxProperties, Integer maxUsers) {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .code("TEST")
                .name("Test")
                .maxProperties(maxProperties)
                .maxUsers(maxUsers)
                .features(new HashMap<>())
                .build();
        Subscription subscription = Subscription.builder()
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .build();
        when(subscriptionRepository.findActiveByTenantId(tenantId))
                .thenReturn(Optional.of(subscription));
    }

    @Test
    void checkPropertyLimit_UnderLimit_DoesNotThrow() {
        stubActiveSubscription(5, 5);

        assertThatCode(() -> planEnforcementService.checkPropertyLimit(tenantId, () -> 3L))
                .doesNotThrowAnyException();
    }

    @Test
    void checkPropertyLimit_AtLimit_ThrowsTenantLimitExceeded() {
        stubActiveSubscription(5, 5);

        assertThatThrownBy(() -> planEnforcementService.checkPropertyLimit(tenantId, () -> 5L))
                .isInstanceOf(TenantLimitExceededException.class)
                .hasMessageContaining("properties");
    }

    @Test
    void checkPropertyLimit_OverLimit_Throws() {
        stubActiveSubscription(5, 5);

        assertThatThrownBy(() -> planEnforcementService.checkPropertyLimit(tenantId, () -> 6L))
                .isInstanceOf(TenantLimitExceededException.class);
    }

    @Test
    void checkPropertyLimit_UnlimitedPlan_NeverThrows_AndSkipsCountSupplier() {
        stubActiveSubscription(null, null);
        boolean[] supplierCalled = {false};

        planEnforcementService.checkPropertyLimit(tenantId, () -> {
            supplierCalled[0] = true;
            return Long.MAX_VALUE;
        });

        assertThat(supplierCalled[0])
                .as("count supplier must not be evaluated for unlimited plans")
                .isFalse();
    }

    @Test
    void checkUserLimit_AtLimit_Throws() {
        stubActiveSubscription(5, 5);

        assertThatThrownBy(() -> planEnforcementService.checkUserLimit(tenantId, () -> 5L))
                .isInstanceOf(TenantLimitExceededException.class)
                .hasMessageContaining("users");
    }

    @Test
    void checkPropertyLimit_NoActiveSubscription_ThrowsIllegalState() {
        when(subscriptionRepository.findActiveByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planEnforcementService.checkPropertyLimit(tenantId, () -> 0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active subscription");
    }
}
