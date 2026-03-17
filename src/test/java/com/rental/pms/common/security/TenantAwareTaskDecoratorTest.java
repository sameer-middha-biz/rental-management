package com.rental.pms.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAwareTaskDecoratorTest {

    private final TenantAwareTaskDecorator decorator = new TenantAwareTaskDecorator();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void decorate_ShouldPropagateTenantContextToDecoratedRunnable() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        AtomicReference<UUID> capturedTenantId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Runnable decorated = decorator.decorate(() -> {
            capturedTenantId.set(TenantContext.getTenantId());
            latch.countDown();
        });

        // Clear context on original thread to prove propagation works
        TenantContext.clear();

        new Thread(decorated).start();
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedTenantId.get()).isEqualTo(tenantId);
    }

    @Test
    void decorate_ShouldPropagateSecurityContextToDecoratedRunnable() throws Exception {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "pass", "ROLE_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(auth);

        AtomicReference<String> capturedUsername = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Runnable decorated = decorator.decorate(() -> {
            SecurityContext ctx = SecurityContextHolder.getContext();
            if (ctx.getAuthentication() != null) {
                capturedUsername.set(ctx.getAuthentication().getName());
            }
            latch.countDown();
        });

        SecurityContextHolder.clearContext();

        new Thread(decorated).start();
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedUsername.get()).isEqualTo("user");
    }

    @Test
    void decorate_ShouldPropagateMdcToDecoratedRunnable() throws Exception {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        MDC.put("tenantId", UUID.randomUUID().toString());

        AtomicReference<Map<String, String>> capturedMdc = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Runnable decorated = decorator.decorate(() -> {
            capturedMdc.set(MDC.getCopyOfContextMap());
            latch.countDown();
        });

        MDC.clear();

        new Thread(decorated).start();
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedMdc.get()).containsEntry("requestId", requestId);
    }

    @Test
    void decorate_ShouldClearContextAfterExecution() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        MDC.put("requestId", "test");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user", "pass"));

        AtomicReference<UUID> tenantAfterRun = new AtomicReference<>();
        AtomicReference<Map<String, String>> mdcAfterRun = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Runnable inner = () -> {
            // Context should be available here
        };

        Runnable decorated = decorator.decorate(inner);

        new Thread(() -> {
            decorated.run();
            // After run, context should be cleared
            tenantAfterRun.set(TenantContext.getTenantId());
            mdcAfterRun.set(MDC.getCopyOfContextMap());
            latch.countDown();
        }).start();

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(tenantAfterRun.get()).isNull();
        assertThat(mdcAfterRun.get()).isNullOrEmpty();
    }

    @Test
    void decorate_WhenNoTenantContext_ShouldNotFail() throws Exception {
        // No TenantContext set
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<UUID> capturedTenantId = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            capturedTenantId.set(TenantContext.getTenantId());
            latch.countDown();
        });

        new Thread(decorated).start();
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedTenantId.get()).isNull();
    }
}
