package com.rental.pms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.pms.common.dto.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Per-tenant and per-IP rate limiting using Bucket4j.
 * Uses Redis (Lettuce) for distributed rate limit state in production,
 * and falls back to in-memory buckets when no ProxyManager is available.
 * <p>
 * Authenticated requests are rate-limited by tenantId.
 * Anonymous requests are rate-limited by IP address.
 * Returns HTTP 429 with Retry-After header when limit is exceeded.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<String> proxyManager;
    private final ObjectMapper objectMapper;
    private final int tenantRequestsPerMinute;
    private final int anonymousRequestsPerMinute;

    // Bounded fallback cache when Redis ProxyManager is unavailable (tests, Redis outage).
    private final Cache<String, Bucket> localBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(5))
            .build();

    public RateLimitFilter(
            @Autowired(required = false) ProxyManager<String> proxyManager,
            ObjectMapper objectMapper,
            @Value("${pms.rate-limit.tenant-requests-per-minute:100}") int tenantRequestsPerMinute,
            @Value("${pms.rate-limit.anonymous-requests-per-minute:20}") int anonymousRequestsPerMinute
    ) {
        this.proxyManager = proxyManager;
        this.objectMapper = objectMapper;
        this.tenantRequestsPerMinute = tenantRequestsPerMinute;
        this.anonymousRequestsPerMinute = anonymousRequestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String bucketKey;
        int requestsPerMinute;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtUserDetails userDetails) {
            UUID tenantId = userDetails.tenantId();
            bucketKey = "rate-limit:tenant:" + (tenantId != null ? tenantId : userDetails.userId());
            requestsPerMinute = tenantRequestsPerMinute;
        } else {
            String clientIp = getClientIp(request);
            bucketKey = "rate-limit:ip:" + clientIp;
            requestsPerMinute = anonymousRequestsPerMinute;
        }

        Bucket bucket = resolveBucket(bucketKey, requestsPerMinute);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long waitSeconds = Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1;
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            response.setContentType("application/json");
            ErrorResponse errorResponse = new ErrorResponse(
                    Instant.now(), 429, "Too Many Requests",
                    "Rate limit exceeded. Try again in " + waitSeconds + " seconds.",
                    "RATE_LIMIT.EXCEEDED", request.getRequestURI(), null);
            objectMapper.writeValue(response.getWriter(), errorResponse);
            log.warn("Rate limit exceeded for key: {}", bucketKey);
        }
    }

    private Bucket resolveBucket(String key, int requestsPerMinute) {
        if (proxyManager != null) {
            Supplier<BucketConfiguration> configSupplier = () -> buildConfig(requestsPerMinute);
            return proxyManager.builder().build(key, configSupplier);
        }
        // Fallback: bounded in-memory bucket (for unit tests or when Redis is unavailable)
        return localBuckets.get(key, k -> Bucket.builder()
                .addLimit(Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1)))
                .build());
    }

    private BucketConfiguration buildConfig(int requestsPerMinute) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        // Use remoteAddr only — X-Forwarded-For is client-controlled and can be spoofed.
        // The reverse proxy (Traefik/Caddy in Coolify) should set remoteAddr correctly.
        // If behind a trusted proxy, configure Spring's ForwardedHeaderFilter instead.
        return request.getRemoteAddr();
    }
}
