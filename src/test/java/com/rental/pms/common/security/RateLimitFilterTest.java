package com.rental.pms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private RateLimitFilter rateLimitFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // Use in-memory buckets (null ProxyManager) with low limits for testing
        rateLimitFilter = new RateLimitFilter(null, objectMapper, 5, 3);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_WhenWithinLimit_ShouldAllowRequest() throws ServletException, IOException {
        request.setRemoteAddr("192.168.1.1");

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenAnonymousExceedsLimit_ShouldReturn429() throws ServletException, IOException {
        request.setRemoteAddr("10.0.0.1");

        // Exhaust 3 anonymous requests
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, resp, filterChain);
            assertThat(resp.getStatus()).isEqualTo(200);
        }

        // 4th request should be rate-limited
        MockHttpServletResponse limitedResponse = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(request, limitedResponse, filterChain);

        assertThat(limitedResponse.getStatus()).isEqualTo(429);
        assertThat(limitedResponse.getHeader("Retry-After")).isNotNull();
        assertThat(limitedResponse.getContentAsString()).contains("RATE_LIMIT.EXCEEDED");
        // filterChain was called 3 times (once per allowed request), but NOT for the 4th (rate-limited)
        verify(filterChain, times(3)).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_WhenAuthenticatedExceedsLimit_ShouldReturn429() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        setAuthentication(UUID.randomUUID(), tenantId);

        // Exhaust 5 tenant requests
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, resp, filterChain);
            assertThat(resp.getStatus()).isEqualTo(200);
        }

        // 6th request should be rate-limited
        MockHttpServletResponse limitedResponse = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(request, limitedResponse, filterChain);

        assertThat(limitedResponse.getStatus()).isEqualTo(429);
        assertThat(limitedResponse.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void doFilterInternal_ShouldUseRemoteAddrNotXForwardedFor() throws ServletException, IOException {
        // X-Forwarded-For should be ignored to prevent spoofing (Issue #4)
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);

        // Exhaust limit using the same remoteAddr, proving it uses remoteAddr not XFF
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, resp, filterChain);
        }

        MockHttpServletResponse limitedResponse = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(request, limitedResponse, filterChain);
        assertThat(limitedResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void doFilterInternal_DifferentTenantsHaveSeparateLimits() throws ServletException, IOException {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        // Exhaust tenant A's limit
        setAuthentication(UUID.randomUUID(), tenantA);
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, resp, filterChain);
        }

        // Tenant B should still have full limit
        setAuthentication(UUID.randomUUID(), tenantB);
        MockHttpServletResponse tenantBResponse = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(request, tenantBResponse, filterChain);

        assertThat(tenantBResponse.getStatus()).isEqualTo(200);
    }

    private void setAuthentication(UUID userId, UUID tenantId) {
        JwtUserDetails userDetails = new JwtUserDetails(userId, tenantId, List.of("AGENCY_ADMIN"), List.of());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
