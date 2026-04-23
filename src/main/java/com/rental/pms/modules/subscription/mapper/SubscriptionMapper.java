package com.rental.pms.modules.subscription.mapper;

import com.rental.pms.modules.subscription.dto.SubscriptionPlanResponse;
import com.rental.pms.modules.subscription.dto.SubscriptionResponse;
import com.rental.pms.modules.subscription.entity.Subscription;
import com.rental.pms.modules.subscription.entity.SubscriptionPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan);

    @Mapping(target = "status", expression = "java(subscription.getStatus().name())")
    SubscriptionResponse toResponse(Subscription subscription);
}
