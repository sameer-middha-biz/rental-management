package com.rental.pms.modules.subscription.repository;

import com.rental.pms.modules.subscription.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByCode(String code);

    List<SubscriptionPlan> findAllByIsActiveTrueOrderBySortOrderAsc();
}
