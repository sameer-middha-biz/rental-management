package com.rental.pms.modules.tenant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.pms.common.security.JwtAuthenticationFilter;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.RateLimitFilter;
import com.rental.pms.common.security.TenantFilter;
import com.rental.pms.modules.tenant.dto.TenantResponse;
import com.rental.pms.modules.tenant.dto.UpdateTenantRequest;
import com.rental.pms.modules.tenant.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TenantController Tests")
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TenantService tenantService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private TenantFilter tenantFilter;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    private TenantResponse createSampleTenantResponse() {
        return new TenantResponse(
                UUID.randomUUID(),
                "Doe Rentals",
                "doe-rentals",
                null,
                "UTC",
                "GBP",
                "PERCENTAGE",
                BigDecimal.valueOf(15.00),
                null,
                null,
                "admin@doerentals.com",
                "+44123456789",
                "123 Main Street, London",
                "ACTIVE",
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/tenant should return 200")
    void getTenant_ShouldReturn200() throws Exception {
        TenantResponse tenantResponse = createSampleTenantResponse();
        given(tenantService.getTenant()).willReturn(tenantResponse);

        mockMvc.perform(get("/api/v1/tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Doe Rentals"))
                .andExpect(jsonPath("$.slug").value("doe-rentals"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/v1/tenant should return 200")
    void updateTenant_ShouldReturn200() throws Exception {
        UpdateTenantRequest request = new UpdateTenantRequest(
                "Updated Rentals",
                null,
                "Europe/London",
                "GBP",
                "PERCENTAGE",
                BigDecimal.valueOf(20.00),
                null,
                "contact@updated.com",
                "+44987654321",
                "456 New Street, London"
        );

        TenantResponse updatedResponse = new TenantResponse(
                UUID.randomUUID(),
                "Updated Rentals",
                "doe-rentals",
                null,
                "Europe/London",
                "GBP",
                "PERCENTAGE",
                BigDecimal.valueOf(20.00),
                null,
                null,
                "contact@updated.com",
                "+44987654321",
                "456 New Street, London",
                "ACTIVE",
                Instant.now(),
                Instant.now()
        );

        given(tenantService.updateTenant(any(UpdateTenantRequest.class))).willReturn(updatedResponse);

        mockMvc.perform(put("/api/v1/tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Rentals"))
                .andExpect(jsonPath("$.timezone").value("Europe/London"));
    }
}
