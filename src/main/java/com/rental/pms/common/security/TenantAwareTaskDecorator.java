package com.rental.pms.common.security;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Propagates TenantContext, SecurityContext, and MDC to @Async threads.
 * Without this decorator, async operations (audit, email, SMS, webhooks)
 * would lose tenant isolation and security context.
 */
public class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        UUID tenantId = TenantContext.getTenantId();
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        return () -> {
            try {
                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                }
                SecurityContextHolder.setContext(securityContext);
                if (mdcContext != null) {
                    MDC.setContextMap(mdcContext);
                }
                runnable.run();
            } finally {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
                MDC.clear();
            }
        };
    }
}
