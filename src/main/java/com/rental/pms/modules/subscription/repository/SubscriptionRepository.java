package com.rental.pms.modules.subscription.repository;

import com.rental.pms.modules.subscription.entity.Subscription;
import com.rental.pms.modules.subscription.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("""
            SELECT s FROM Subscription s
            WHERE s.tenantId = :tenantId
              AND s.status IN :activeStatuses
            """)
    Optional<Subscription> findActiveByTenantId(UUID tenantId, List<SubscriptionStatus> activeStatuses);

    default Optional<Subscription> findActiveByTenantId(UUID tenantId) {
        return findActiveByTenantId(tenantId,
                List.of(SubscriptionStatus.TRIALING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE));
    }
}
