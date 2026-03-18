package com.rental.pms.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

/**
 * Bucket4j + Redis (Lettuce) rate limiting configuration.
 * Provides a distributed ProxyManager for rate limit bucket state.
 * Reuses the Spring Data Redis connection factory instead of creating a separate RedisClient.
 * <p>
 * Only active when pms.rate-limit.enabled=true (default).
 * When disabled or Redis unavailable, RateLimitFilter falls back to in-memory buckets.
 */
@Configuration
@Slf4j
public class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(name = "pms.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
    public ProxyManager<String> proxyManager(LettuceConnectionFactory connectionFactory) {
        try {
            RedisClient redisClient = (RedisClient) connectionFactory.getNativeClient();
            StatefulRedisConnection<String, byte[]> connection =
                    redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

            log.info("Bucket4j Redis ProxyManager initialized using Spring Data Redis connection");

            return LettuceBasedProxyManager.builderFor(connection)
                    .withExpirationStrategy(
                            ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                    .build();

        } catch (Exception ex) {
            log.warn("Failed to initialize Redis ProxyManager for rate limiting. "
                    + "Falling back to in-memory rate limiting. Error: {}", ex.getMessage());
            return null;
        }
    }
}
