package com.rental.pms.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;

/**
 * Custom permission evaluator for use in @PreAuthorize SpEL expressions.
 * Checks whether the current authenticated user has a specific permission
 * (granted authority) from their JWT claims.
 * <p>
 * Usage: @PreAuthorize("@permissionEvaluator.hasPermission('USER_MANAGE')")
 */
@Component("permissionEvaluator")
public class PmsPermissionEvaluator {

    public boolean hasPermission(String permission) {
        Collection<? extends GrantedAuthority> authorities = getAuthorities();
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equals(permission));
    }

    public boolean hasAnyPermission(String... permissions) {
        Collection<? extends GrantedAuthority> authorities = getAuthorities();
        return authorities.stream()
                .anyMatch(a -> Arrays.asList(permissions).contains(a.getAuthority()));
    }

    private Collection<? extends GrantedAuthority> getAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return java.util.List.of();
        }
        return authentication.getAuthorities();
    }
}
