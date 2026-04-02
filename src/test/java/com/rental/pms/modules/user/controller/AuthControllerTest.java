package com.rental.pms.modules.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.pms.common.security.JwtAuthenticationFilter;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.RateLimitFilter;
import com.rental.pms.common.security.TenantFilter;
import com.rental.pms.modules.tenant.dto.TenantRegistrationRequest;
import com.rental.pms.modules.tenant.service.TenantRegistrationService;
import com.rental.pms.modules.user.dto.AuthResponse;
import com.rental.pms.modules.user.dto.LoginRequest;
import com.rental.pms.modules.user.dto.RefreshTokenRequest;
import com.rental.pms.modules.user.service.AuthService;
import com.rental.pms.modules.user.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TenantRegistrationService tenantRegistrationService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private TenantFilter tenantFilter;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    @Test
    @DisplayName("POST /api/v1/auth/register with valid request should return 201")
    void register_WithValidRequest_ShouldReturn201() throws Exception {
        TenantRegistrationRequest request = new TenantRegistrationRequest(
                "admin@agency.com",
                "SecurePass123!",
                "John",
                "Doe",
                "Doe Rentals"
        );

        AuthResponse authResponse = new AuthResponse("access-token-123", "refresh-token-456", 900L);
        given(tenantRegistrationService.register(any(TenantRegistrationRequest.class))).willReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token-123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-456"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register with invalid email should return 400")
    void register_WithInvalidEmail_ShouldReturn400() throws Exception {
        String invalidRequest = """
                {
                    "email": "not-an-email",
                    "password": "SecurePass123!",
                    "firstName": "John",
                    "lastName": "Doe",
                    "agencyName": "Doe Rentals"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register with missing fields should return 400")
    void register_WithMissingFields_ShouldReturn400() throws Exception {
        String incompleteRequest = """
                {
                    "email": "admin@agency.com",
                    "password": "SecurePass123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incompleteRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with valid credentials should return 200")
    void login_WithValidCredentials_ShouldReturn200() throws Exception {
        LoginRequest request = new LoginRequest("admin@agency.com", "SecurePass123!");

        AuthResponse authResponse = new AuthResponse("access-token-789", "refresh-token-012", 900L);
        given(authService.login(any(LoginRequest.class))).willReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-789"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-012"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh with valid token should return 200")
    void refresh_WithValidToken_ShouldReturn200() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");

        AuthResponse authResponse = new AuthResponse("new-access-token", "new-refresh-token", 900L);
        given(authService.refreshToken(any(RefreshTokenRequest.class))).willReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/auth/logout with authentication should return 204")
    void logout_WithAuthentication_ShouldReturn204() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token-to-invalidate");

        doNothing().when(authService).logout(any(RefreshTokenRequest.class));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }
}
