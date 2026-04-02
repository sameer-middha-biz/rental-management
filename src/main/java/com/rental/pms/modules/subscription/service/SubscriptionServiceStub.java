package com.rental.pms.modules.subscription.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Stub implementation of SubscriptionService.
 * Will be replaced with full implementation in Phase 4.
 */
@Service
@Slf4j
public class SubscriptionServiceStub implements SubscriptionService {

    @Override
    public void createStarterSubscription(UUID tenantId) {
        log.info("Stub: Created starter subscription for tenant {}", tenantId);
    }
}
