package com.rental.pms.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PmsPermissionEvaluatorTest {

    private PmsPermissionEvaluator permissionEvaluator;

    @BeforeEach
    void setUp() {
        permissionEvaluator = new PmsPermissionEvaluator();

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("USER_MANAGE"),
                new SimpleGrantedAuthority("PROPERTY_VIEW"),
                new SimpleGrantedAuthority("BOOKING_CREATE")
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("user", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hasPermission_WhenAuthorityPresent_ShouldReturnTrue() {
        // Act & Assert
        assertThat(permissionEvaluator.hasPermission("USER_MANAGE")).isTrue();
    }

    @Test
    void hasPermission_WhenAuthorityAbsent_ShouldReturnFalse() {
        // Act & Assert
        assertThat(permissionEvaluator.hasPermission("TENANT_DELETE")).isFalse();
    }

    @Test
    void hasAnyPermission_WhenOneMatches_ShouldReturnTrue() {
        // Act & Assert
        assertThat(permissionEvaluator.hasAnyPermission("TENANT_DELETE", "BOOKING_CREATE")).isTrue();
    }

    @Test
    void hasAnyPermission_WhenNoneMatch_ShouldReturnFalse() {
        // Act & Assert
        assertThat(permissionEvaluator.hasAnyPermission("TENANT_DELETE", "PAYMENT_PROCESS")).isFalse();
    }
}
