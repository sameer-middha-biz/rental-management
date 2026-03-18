package com.rental.pms.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Helper to extract current user details from SecurityContextHolder.
 * The Authentication principal is set by JwtAuthenticationFilter as a {@link JwtUserDetails} record.
 */
@Component
public class CurrentUser {

    /**
     * Returns the current authenticated user's ID, or null if unauthenticated.
     */
    public UUID getUserId() {
        JwtUserDetails details = getUserDetails();
        return details != null ? details.userId() : null;
    }

    /**
     * Returns the current authenticated user's tenant ID, or null if unauthenticated.
     */
    public UUID getTenantId() {
        JwtUserDetails details = getUserDetails();
        return details != null ? details.tenantId() : null;
    }

    /**
     * Returns the current authenticated user's roles, or an empty list if unauthenticated.
     */
    public List<String> getRoles() {
        JwtUserDetails details = getUserDetails();
        return details != null ? details.roles() : List.of();
    }

    /**
     * Returns the current authenticated user's permissions, or an empty list if unauthenticated.
     */
    public List<String> getPermissions() {
        JwtUserDetails details = getUserDetails();
        return details != null ? details.permissions() : List.of();
    }

    /**
     * Returns the full JwtUserDetails from the SecurityContext, or null if not authenticated.
     */
    public JwtUserDetails getUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtUserDetails details) {
            return details;
        }
        return null;
    }

    /**
     * Returns true if the current user has the SUPER_ADMIN role.
     */
    public boolean isSuperAdmin() {
        return getRoles().contains("SUPER_ADMIN");
    }
}
