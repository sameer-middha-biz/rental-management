package com.rental.pms.config;

import com.rental.pms.common.security.TenantAwareTaskDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async thread pool configuration with TenantAwareTaskDecorator
 * to propagate TenantContext, SecurityContext, and MDC to async threads.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;
    private final String threadNamePrefix;

    public AsyncConfig(
            @Value("${pms.async.core-pool-size}") int corePoolSize,
            @Value("${pms.async.max-pool-size}") int maxPoolSize,
            @Value("${pms.async.queue-capacity}") int queueCapacity,
            @Value("${pms.async.thread-name-prefix}") String threadNamePrefix) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
        this.threadNamePrefix = threadNamePrefix;
    }

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setTaskDecorator(new TenantAwareTaskDecorator());
        executor.initialize();
        return executor;
    }
}
