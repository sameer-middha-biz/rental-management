package com.rental.pms.config;

import com.rental.pms.common.multitenancy.TenantHibernateFilter;
import com.rental.pms.common.security.JwtAuthenticationFilter;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.RateLimitFilter;
import com.rental.pms.common.security.TenantFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying SecurityConfig filter chain:
 * - Public endpoints are NOT blocked by security (no 401/403)
 * - Protected endpoints return 401 without token
 * - Protected endpoints pass security with valid token (no 401/403)
 * <p>
 * Uses @WebMvcTest to test only the web/security layer without requiring a database.
 * Note: Without actual controllers, public/authenticated endpoints may return 404 or 500
 * depending on the GlobalExceptionHandler. The key assertion is that security does NOT block them.
 */
@WebMvcTest
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        TenantFilter.class,
        RateLimitFilter.class,
        SecurityConfigIntegrationTest.TestSecurityBeans.class
})
@ActiveProfiles("test")
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * Provides beans needed by the security filters that aren't loaded by @WebMvcTest.
     */
    static class TestSecurityBeans {

        @Bean
        public JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider(
                    new ClassPathResource("keys/private.pem"),
                    new ClassPathResource("keys/public.pem"),
                    Duration.ofMinutes(15),
                    Duration.ofDays(7)
            );
        }

        @Bean
        public TenantHibernateFilter tenantHibernateFilter() {
            // No-op for web layer tests — no Hibernate session needed
            return new TenantHibernateFilter(null) {
                @Override
                public void enableFilter() {
                    // no-op in web layer test
                }

                @Override
                public void disableFilter() {
                    // no-op in web layer test
                }
            };
        }
    }

    @Test
    void authEndpoints_ShouldNotBeBlockedBySecurity() throws Exception {
        // Auth endpoints are public — should NOT return 401 or 403
        int status = mockMvc.perform(get("/api/v1/auth/some-endpoint"))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
                .isNotEqualTo(401)
                .isNotEqualTo(403);
    }

    @Test
    void publicEndpoints_ShouldNotBeBlockedBySecurity() throws Exception {
        int status = mockMvc.perform(get("/api/v1/public/some-endpoint"))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
                .isNotEqualTo(401)
                .isNotEqualTo(403);
    }

    @Test
    void webhookEndpoints_ShouldNotBeBlockedBySecurity() throws Exception {
        int status = mockMvc.perform(get("/api/v1/webhooks/stripe"))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
                .isNotEqualTo(401)
                .isNotEqualTo(403);
    }

    @Test
    void protectedEndpoint_WithoutToken_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_WithValidToken_ShouldNotBeBlockedBySecurity() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of("AGENCY_ADMIN"),
                List.of("PROPERTY_VIEW")
        );

        // Should NOT be 401 or 403 — security passes, no controller returns 404 or 500
        int status = mockMvc.perform(get("/api/v1/properties")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
                .isNotEqualTo(401)
                .isNotEqualTo(403);
    }

    @Test
    void protectedEndpoint_WithExpiredToken_ShouldReturn401() throws Exception {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(
                new ClassPathResource("keys/private.pem"),
                new ClassPathResource("keys/public.pem"),
                Duration.ofSeconds(0),
                Duration.ofSeconds(0)
        );

        String token = shortLivedProvider.generateAccessToken(
                UUID.randomUUID(), UUID.randomUUID(), List.of("AGENCY_ADMIN"), List.of());

        mockMvc.perform(get("/api/v1/properties")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_WithInvalidToken_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/properties")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }
}
