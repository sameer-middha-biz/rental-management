package com.rental.pms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.impl.DefaultClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new JwtAuthenticationFilter(jwtTokenProvider, objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_WhenValidToken_ShouldSetAuthentication() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<String> roles = List.of("AGENCY_ADMIN");
        List<String> permissions = List.of("PROPERTY_CREATE");

        Claims claims = new DefaultClaims(Map.of(
                "sub", userId.toString(),
                "tenantId", tenantId.toString(),
                "roles", roles,
                "permissions", permissions
        ));

        request.addHeader("Authorization", "Bearer valid.token.here");
        when(jwtTokenProvider.validateAndExtractClaims("valid.token.here")).thenReturn(claims);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(userId);
        when(jwtTokenProvider.getTenantId(claims)).thenReturn(tenantId);
        when(jwtTokenProvider.getRoles(claims)).thenReturn(roles);
        when(jwtTokenProvider.getPermissions(claims)).thenReturn(permissions);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(JwtUserDetails.class);

        JwtUserDetails userDetails = (JwtUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertThat(userDetails.userId()).isEqualTo(userId);
        assertThat(userDetails.tenantId()).isEqualTo(tenantId);
        assertThat(userDetails.roles()).containsExactly("AGENCY_ADMIN");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenNoAuthorizationHeader_ShouldSkipAndContinueChain() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider, never()).validateAndExtractClaims(anyString());
    }

    @Test
    void doFilterInternal_WhenNonBearerHeader_ShouldSkipAndContinueChain() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenExpiredToken_ShouldReturn401() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer expired.token");
        when(jwtTokenProvider.validateAndExtractClaims("expired.token"))
                .thenThrow(new ExpiredJwtException(null, null, "Token expired"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH.TOKEN.EXPIRED");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenInvalidToken_ShouldReturn401() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer invalid.token");
        when(jwtTokenProvider.validateAndExtractClaims("invalid.token"))
                .thenThrow(new JwtException("Invalid signature"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH.TOKEN.INVALID");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenRefreshToken_ShouldSkipAuthAndContinueChain() throws ServletException, IOException {
        Claims claims = new DefaultClaims(Map.of(
                "sub", UUID.randomUUID().toString(),
                "type", "refresh"
        ));

        request.addHeader("Authorization", "Bearer refresh.token.here");
        when(jwtTokenProvider.validateAndExtractClaims("refresh.token.here")).thenReturn(claims);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenValidToken_ShouldSetRoleAndPermissionAuthorities() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Claims claims = new DefaultClaims(Map.of(
                "sub", userId.toString(),
                "tenantId", tenantId.toString(),
                "roles", List.of("PROPERTY_MANAGER"),
                "permissions", List.of("BOOKING_CREATE", "BOOKING_VIEW")
        ));

        request.addHeader("Authorization", "Bearer valid.token");
        when(jwtTokenProvider.validateAndExtractClaims("valid.token")).thenReturn(claims);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(userId);
        when(jwtTokenProvider.getTenantId(claims)).thenReturn(tenantId);
        when(jwtTokenProvider.getRoles(claims)).thenReturn(List.of("PROPERTY_MANAGER"));
        when(jwtTokenProvider.getPermissions(claims)).thenReturn(List.of("BOOKING_CREATE", "BOOKING_VIEW"));

        filter.doFilterInternal(request, response, filterChain);

        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertThat(authorities).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_PROPERTY_MANAGER", "BOOKING_CREATE", "BOOKING_VIEW");
    }
}
