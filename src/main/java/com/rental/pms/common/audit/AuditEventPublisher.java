package com.rental.pms.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Convenience publisher for audit events.
 * Services use this to publish audit trail entries without
 * directly coupling to Spring's ApplicationEventPublisher.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(UUID tenantId, UUID userId, String action,
                        String entityType, UUID entityId, String details) {
        AuditEvent event = AuditEvent.of(tenantId, userId, action, entityType, entityId, details);
        log.debug("Publishing audit event: action={}, entityType={}, entityId={}",
                action, entityType, entityId);
        applicationEventPublisher.publishEvent(event);
    }
}
