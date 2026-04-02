package com.rental.pms.modules.tenant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.common.security.JwtAuthenticationFilter;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.RateLimitFilter;
import com.rental.pms.common.security.TenantFilter;
import com.rental.pms.modules.tenant.dto.TenantResponse;
import com.rental.pms.modules.tenant.dto.UpdateTenantStatusRequest;
import com.rental.pms.modules.tenant.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminTenantController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminTenantController Tests")
class AdminTenantControllerTest {

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

    private TenantResponse createSampleTenantResponse(UUID id, String name, String slug) {
        return new TenantResponse(
                id,
                name,
                slug,
                null,
                "UTC",
                "GBP",
                "PERCENTAGE",
                BigDecimal.valueOf(15.00),
                null,
                null,
                "admin@" + slug + ".com",
                null,
                null,
                "ACTIVE",
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/admin/tenants should return 200")
    void getAllTenants_ShouldReturn200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantResponse tenant = createSampleTenantResponse(tenantId, "Doe Rentals", "doe-rentals");
        PageResponse<TenantResponse> pageResponse = new PageResponse<>(
                List.of(tenant), 1L, 1, 0
        );

        given(tenantService.getAllTenants(any(Pageable.class))).willReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/tenants")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Doe Rentals"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.currentPage").value(0));
    }

    @Test
    @WithMockUser
    @DisplayName("PATCH /api/v1/admin/tenants/{id}/status should return 200")
    void updateTenantStatus_ShouldReturn200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UpdateTenantStatusRequest request = new UpdateTenantStatusRequest("SUSPENDED");

        TenantResponse updatedTenant = new TenantResponse(
                tenantId,
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
                null,
                null,
                "SUSPENDED",
                Instant.now(),
                Instant.now()
        );

        given(tenantService.updateTenantStatus(eq(tenantId), any(UpdateTenantStatusRequest.class)))
                .willReturn(updatedTenant);

        mockMvc.perform(patch("/api/v1/admin/tenants/{id}/status", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.name").value("Doe Rentals"));
    }
}
