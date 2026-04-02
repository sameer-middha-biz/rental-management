package com.rental.pms.modules.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.common.security.JwtAuthenticationFilter;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.RateLimitFilter;
import com.rental.pms.common.security.TenantFilter;
import com.rental.pms.modules.user.dto.AcceptInviteRequest;
import com.rental.pms.modules.user.dto.AuthResponse;
import com.rental.pms.modules.user.dto.InvitationResponse;
import com.rental.pms.modules.user.dto.InviteUserRequest;
import com.rental.pms.modules.user.service.InvitationService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvitationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InvitationController Tests")
class InvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InvitationService invitationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private TenantFilter tenantFilter;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    private InvitationResponse createSampleInvitationResponse() {
        return new InvitationResponse(
                UUID.randomUUID(),
                "newuser@agency.com",
                "PROPERTY_MANAGER",
                "PENDING",
                Instant.now().plus(7, ChronoUnit.DAYS),
                Instant.now()
        );
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/invitations should return 201")
    void invite_ShouldReturn201() throws Exception {
        InviteUserRequest request = new InviteUserRequest("newuser@agency.com", "PROPERTY_MANAGER");
        InvitationResponse response = createSampleInvitationResponse();

        given(invitationService.invite(any(InviteUserRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@agency.com"))
                .andExpect(jsonPath("$.roleName").value("PROPERTY_MANAGER"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/invitations should return 200")
    void getInvitations_ShouldReturn200() throws Exception {
        InvitationResponse invitation = createSampleInvitationResponse();
        PageResponse<InvitationResponse> pageResponse = new PageResponse<>(
                List.of(invitation), 1L, 1, 0
        );

        given(invitationService.getInvitations(any(Pageable.class))).willReturn(pageResponse);

        mockMvc.perform(get("/api/v1/invitations")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].email").value("newuser@agency.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/invitations/{token}/accept (public endpoint) should return 201")
    void acceptInvite_ShouldReturn201() throws Exception {
        String inviteToken = "valid-invite-token-abc123";
        AcceptInviteRequest request = new AcceptInviteRequest("SecurePass123!", "Jane", "Doe");

        AuthResponse authResponse = new AuthResponse("access-token-new", "refresh-token-new", 900L);
        given(invitationService.acceptInvite(eq(inviteToken), any(AcceptInviteRequest.class)))
                .willReturn(authResponse);

        mockMvc.perform(post("/api/v1/invitations/{token}/accept", inviteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token-new"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-new"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/v1/invitations/{id} should return 204")
    void revokeInvitation_ShouldReturn204() throws Exception {
        UUID invitationId = UUID.randomUUID();

        doNothing().when(invitationService).revokeInvitation(invitationId);

        mockMvc.perform(delete("/api/v1/invitations/{id}", invitationId))
                .andExpect(status().isNoContent());
    }
}
