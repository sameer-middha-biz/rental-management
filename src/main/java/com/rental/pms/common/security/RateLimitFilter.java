package com.rental.pms.common.security;

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

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final int tenantRequestsPerMinute;
    private final int anonymousRequestsPerMinute;

    // Fallback in-memory buckets when Redis ProxyManager is unavailable (tests, Redis outage).
    // Note: this map grows unboundedly — acceptable for fallback-only scenarios.
    // In production, Redis should always be available; if it goes down temporarily,
    // the map will be bounded by unique IPs/tenants during the outage window.
    private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Autowired(required = false) ProxyManager<String> proxyManager,
            @Value("${pms.rate-limit.tenant-requests-per-minute:100}") int tenantRequestsPerMinute,
            @Value("${pms.rate-limit.anonymous-requests-per-minute:20}") int anonymousRequestsPerMinute
    ) {
        this.proxyManager = proxyManager;
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
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again in "
                            + waitSeconds + " seconds.\",\"errorCode\":\"RATE_LIMIT.EXCEEDED\"}");
            log.warn("Rate limit exceeded for key: {}", bucketKey);
        }
    }

    private Bucket resolveBucket(String key, int requestsPerMinute) {
        if (proxyManager != null) {
            Supplier<BucketConfiguration> configSupplier = () -> buildConfig(requestsPerMinute);
            return proxyManager.builder().build(key, configSupplier);
        }
        // Fallback: in-memory bucket (for unit tests or when Redis is unavailable)
        return localBuckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1)))
                .build());
    }

    private BucketConfiguration buildConfig(int requestsPerMinute) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
