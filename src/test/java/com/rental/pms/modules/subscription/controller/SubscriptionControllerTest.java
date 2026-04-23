package com.rental.pms.modules.subscription.controller;

import com.rental.pms.common.security.JwtAuthenticationFilter;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.RateLimitFilter;
import com.rental.pms.common.security.TenantFilter;
import com.rental.pms.modules.subscription.dto.SubscriptionPlanResponse;
import com.rental.pms.modules.subscription.dto.SubscriptionResponse;
import com.rental.pms.modules.subscription.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SubscriptionController Tests")
class SubscriptionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SubscriptionService subscriptionService;

    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private TenantFilter tenantFilter;
    @MockitoBean private RateLimitFilter rateLimitFilter;

    private SubscriptionPlanResponse starterPlan() {
        return new SubscriptionPlanResponse(
                UUID.randomUUID(), "STARTER", "Starter", "Free tier",
                5, 3, 0L, "GBP", Map.of(), 1);
    }

    @Test
    @WithMockUser(authorities = "SUBSCRIPTION_VIEW")
    @DisplayName("GET /api/v1/subscription returns 200")
    void getCurrent_Returns200() throws Exception {
        SubscriptionResponse resp = new SubscriptionResponse(
                UUID.randomUUID(), UUID.randomUUID(), starterPlan(),
                "ACTIVE", Instant.now(), null, null, null, null, null, Instant.now());
        given(subscriptionService.getCurrentSubscription()).willReturn(resp);

        mockMvc.perform(get("/api/v1/subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plan.code").value("STARTER"))
                .andExpect(jsonPath("$.plan.maxProperties").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/subscription/plans returns 200 (public, no auth needed)")
    void getPlans_Returns200() throws Exception {
        given(subscriptionService.getPlans()).willReturn(List.of(starterPlan()));

        mockMvc.perform(get("/api/v1/subscription/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("STARTER"))
                .andExpect(jsonPath("$[0].monthlyPriceMinorUnits").value(0));
    }
}
