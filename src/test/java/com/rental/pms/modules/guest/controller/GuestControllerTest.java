package com.rental.pms.modules.guest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.pms.common.security.JwtAuthenticationFilter;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.RateLimitFilter;
import com.rental.pms.common.security.TenantFilter;
import com.rental.pms.modules.guest.dto.CreateGuestRequest;
import com.rental.pms.modules.guest.dto.GuestResponse;
import com.rental.pms.modules.guest.dto.UpdateGuestRequest;
import com.rental.pms.modules.guest.service.GuestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GuestController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GuestController Tests")
class GuestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private GuestService guestService;

    // Security plumbing (filters disabled above but beans still required).
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private TenantFilter tenantFilter;
    @MockitoBean private RateLimitFilter rateLimitFilter;

    private GuestResponse sample(UUID id) {
        return new GuestResponse(id, UUID.randomUUID(), "Jane", "Doe",
                "jane@example.com", "+44123", "GBR", null, null,
                0, null, false, null, Instant.now(), Instant.now());
    }

    @Test
    @WithMockUser(authorities = "GUEST_CREATE")
    @DisplayName("POST /api/v1/guests returns 201")
    void create_Returns201() throws Exception {
        UUID id = UUID.randomUUID();
        given(guestService.create(any())).willReturn(sample(id));

        CreateGuestRequest req = new CreateGuestRequest(
                "Jane", "Doe", "jane@example.com", "+44123", "GBR", null, "notes");

        mockMvc.perform(post("/api/v1/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.firstName").value("Jane"));
    }

    @Test
    @WithMockUser(authorities = "GUEST_CREATE")
    @DisplayName("POST /api/v1/guests returns 400 for invalid email")
    void create_InvalidEmail_Returns400() throws Exception {
        CreateGuestRequest bad = new CreateGuestRequest(
                "Jane", "Doe", "not-an-email", null, null, null, null);

        mockMvc.perform(post("/api/v1/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "GUEST_VIEW")
    @DisplayName("GET /api/v1/guests/{id} returns 200")
    void getById_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(guestService.getById(id)).willReturn(sample(id));

        mockMvc.perform(get("/api/v1/guests/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    @WithMockUser(authorities = "GUEST_EDIT")
    @DisplayName("PUT /api/v1/guests/{id} returns updated guest")
    void update_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateGuestRequest req = new UpdateGuestRequest(
                "Janet", null, null, null, null, null, null);
        given(guestService.update(eq(id), any())).willReturn(sample(id));

        mockMvc.perform(put("/api/v1/guests/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "GUEST_MANAGE")
    @DisplayName("DELETE /api/v1/guests/{id}/gdpr returns 204 and calls service")
    void gdprErase_Returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/guests/{id}/gdpr", id))
                .andExpect(status().isNoContent());

        verify(guestService).gdprErase(id);
    }
}
