package com.rental.pms.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTest {

    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new CurrentUser();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUserId_WhenAuthenticated_ShouldReturnUserId() {
        UUID userId = UUID.randomUUID();
        setAuthentication(userId, UUID.randomUUID(), List.of("AGENCY_ADMIN"), List.of());

        assertThat(currentUser.getUserId()).isEqualTo(userId);
    }

    @Test
    void getUserId_WhenUnauthenticated_ShouldReturnNull() {
        assertThat(currentUser.getUserId()).isNull();
    }

    @Test
    void getTenantId_WhenAuthenticated_ShouldReturnTenantId() {
        UUID tenantId = UUID.randomUUID();
        setAuthentication(UUID.randomUUID(), tenantId, List.of("AGENCY_ADMIN"), List.of());

        assertThat(currentUser.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void getRoles_WhenAuthenticated_ShouldReturnRoles() {
        setAuthentication(UUID.randomUUID(), UUID.randomUUID(),
                List.of("AGENCY_ADMIN", "PROPERTY_MANAGER"), List.of());

        assertThat(currentUser.getRoles()).containsExactly("AGENCY_ADMIN", "PROPERTY_MANAGER");
    }

    @Test
    void getRoles_WhenUnauthenticated_ShouldReturnEmptyList() {
        assertThat(currentUser.getRoles()).isEmpty();
    }

    @Test
    void getPermissions_WhenAuthenticated_ShouldReturnPermissions() {
        setAuthentication(UUID.randomUUID(), UUID.randomUUID(),
                List.of(), List.of("PROPERTY_CREATE", "BOOKING_VIEW"));

        assertThat(currentUser.getPermissions()).containsExactly("PROPERTY_CREATE", "BOOKING_VIEW");
    }

    @Test
    void isSuperAdmin_WhenSuperAdminRole_ShouldReturnTrue() {
        setAuthentication(UUID.randomUUID(), null, List.of("SUPER_ADMIN"), List.of());

        assertThat(currentUser.isSuperAdmin()).isTrue();
    }

    @Test
    void isSuperAdmin_WhenNotSuperAdmin_ShouldReturnFalse() {
        setAuthentication(UUID.randomUUID(), UUID.randomUUID(), List.of("AGENCY_ADMIN"), List.of());

        assertThat(currentUser.isSuperAdmin()).isFalse();
    }

    @Test
    void isSuperAdmin_WhenUnauthenticated_ShouldReturnFalse() {
        assertThat(currentUser.isSuperAdmin()).isFalse();
    }

    @Test
    void getUserDetails_WhenNonJwtAuthentication_ShouldReturnNull() {
        // Set a non-JwtUserDetails principal (e.g., String)
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("plain-user", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(currentUser.getUserDetails()).isNull();
        assertThat(currentUser.getUserId()).isNull();
    }

    private void setAuthentication(UUID userId, UUID tenantId, List<String> roles, List<String> permissions) {
        JwtUserDetails userDetails = new JwtUserDetails(userId, tenantId, roles, permissions);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
