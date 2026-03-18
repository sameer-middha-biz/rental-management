package com.rental.pms.common.security;

import com.rental.pms.common.multitenancy.TenantHibernateFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

    @Mock
    private TenantHibernateFilter tenantHibernateFilter;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private TenantFilter tenantFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        MDC.clear();
    }

    @Test
    void doFilterInternal_WhenAuthenticated_ShouldSetTenantContextAndMdc() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        setAuthentication(userId, tenantId, List.of("AGENCY_ADMIN"));

        // Use a flag array to capture MDC values inside the filter chain
        final String[] capturedTenantId = new String[1];
        final String[] capturedUserId = new String[1];
        final String[] capturedRequestId = new String[1];
        final UUID[] capturedTenantContext = new UUID[1];

        tenantFilter.doFilterInternal(request, response, (req, res) -> {
            capturedTenantId[0] = MDC.get("tenantId");
            capturedUserId[0] = MDC.get("userId");
            capturedRequestId[0] = MDC.get("requestId");
            capturedTenantContext[0] = TenantContext.getTenantId();
        });

        assertThat(capturedTenantId[0]).isEqualTo(tenantId.toString());
        assertThat(capturedUserId[0]).isEqualTo(userId.toString());
        assertThat(capturedRequestId[0]).isNotBlank();
        assertThat(capturedTenantContext[0]).isEqualTo(tenantId);
        verify(tenantHibernateFilter).enableFilter();
    }

    @Test
    void doFilterInternal_WhenSuperAdmin_ShouldSkipHibernateFilter() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        setAuthentication(userId, null, List.of("SUPER_ADMIN"));

        tenantFilter.doFilterInternal(request, response, (req, res) -> {
            // Super Admin should not have tenant context set
        });

        verify(tenantHibernateFilter, never()).enableFilter();
    }

    @Test
    void doFilterInternal_ShouldClearTenantContextAndMdcInFinallyBlock() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        setAuthentication(userId, tenantId, List.of("AGENCY_ADMIN"));

        tenantFilter.doFilterInternal(request, response, (req, res) -> {
            // Inside filter chain, context is set
            assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
        });

        // After filter completes, context should be cleared
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void doFilterInternal_WhenFilterChainThrows_ShouldStillClearContext() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        setAuthentication(userId, tenantId, List.of("AGENCY_ADMIN"));

        try {
            tenantFilter.doFilterInternal(request, response, (req, res) -> {
                throw new ServletException("Simulated error");
            });
        } catch (ServletException ignored) {
            // Expected
        }

        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test
    void doFilterInternal_WhenUnauthenticated_ShouldSkipAndContinue() throws ServletException, IOException {
        // No authentication set
        tenantFilter.doFilterInternal(request, response, filterChain);

        assertThat(TenantContext.getTenantId()).isNull();
        verify(tenantHibernateFilter, never()).enableFilter();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ShouldPopulateRequestIdInMdc() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        setAuthentication(userId, tenantId, List.of("PROPERTY_MANAGER"));

        final String[] requestId = new String[1];
        tenantFilter.doFilterInternal(request, response, (req, res) -> {
            requestId[0] = MDC.get("requestId");
        });

        // requestId should be a valid UUID
        assertThat(requestId[0]).isNotBlank();
        assertThat(UUID.fromString(requestId[0])).isNotNull();
    }

    private void setAuthentication(UUID userId, UUID tenantId, List<String> roles) {
        JwtUserDetails userDetails = new JwtUserDetails(userId, tenantId, roles, List.of());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
