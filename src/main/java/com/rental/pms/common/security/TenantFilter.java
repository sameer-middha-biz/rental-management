package com.rental.pms.common.security;

import com.rental.pms.common.multitenancy.TenantHibernateFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Runs after JwtAuthenticationFilter. Extracts tenantId from the authenticated
 * user's JWT claims, sets TenantContext, populates MDC for structured logging,
 * and activates the Hibernate tenant filter.
 * <p>
 * Super Admin requests skip Hibernate filter activation for cross-tenant access.
 * Always clears TenantContext and MDC in the finally block to prevent leakage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_REQUEST_ID = "requestId";

    private final TenantHibernateFilter tenantHibernateFilter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getPrincipal() instanceof JwtUserDetails userDetails) {
                UUID userId = userDetails.userId();
                UUID tenantId = userDetails.tenantId();
                boolean isSuperAdmin = userDetails.roles().contains("SUPER_ADMIN");

                // Populate MDC for structured logging
                MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString());
                MDC.put(MDC_USER_ID, userId.toString());

                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                    MDC.put(MDC_TENANT_ID, tenantId.toString());
                }

                // Super Admin gets cross-tenant access — skip Hibernate filter
                if (!isSuperAdmin && tenantId != null) {
                    tenantHibernateFilter.enableFilter();
                }

                log.debug("Tenant context set: tenantId={}, userId={}, superAdmin={}",
                        tenantId, userId, isSuperAdmin);
            }

            filterChain.doFilter(request, response);

        } finally {
            TenantContext.clear();
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}
