package com.rental.pms.modules.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.common.security.JwtAuthenticationFilter;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.RateLimitFilter;
import com.rental.pms.common.security.TenantFilter;
import com.rental.pms.modules.user.dto.UpdateUserRequest;
import com.rental.pms.modules.user.dto.UserResponse;
import com.rental.pms.modules.user.service.UserService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UserController Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private TenantFilter tenantFilter;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    private UserResponse createSampleUserResponse(UUID id) {
        return new UserResponse(
                id,
                "john@agency.com",
                "John",
                "Doe",
                "+44123456789",
                "ACTIVE",
                List.of("AGENCY_ADMIN"),
                Instant.now()
        );
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users should return 200")
    void getUsers_ShouldReturn200() throws Exception {
        UUID userId = UUID.randomUUID();
        UserResponse user = createSampleUserResponse(userId);
        PageResponse<UserResponse> pageResponse = new PageResponse<>(
                List.of(user), 1L, 1, 0
        );

        given(userService.getUsers(any(Pageable.class))).willReturn(pageResponse);

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].email").value("john@agency.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/me when authenticated should return 200")
    void getCurrentUser_WhenAuthenticated_ShouldReturn200() throws Exception {
        UUID userId = UUID.randomUUID();
        UserResponse currentUser = createSampleUserResponse(userId);

        given(userService.getCurrentUser()).willReturn(currentUser);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@agency.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/{id} should return 200")
    void getUser_ShouldReturn200() throws Exception {
        UUID userId = UUID.randomUUID();
        UserResponse userResponse = createSampleUserResponse(userId);

        given(userService.getUser(userId)).willReturn(userResponse);

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@agency.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/v1/users/{id} should return 200")
    void updateUser_ShouldReturn200() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateUserRequest request = new UpdateUserRequest("Jane", "Smith", "+44999888777");

        UserResponse updatedUser = new UserResponse(
                userId,
                "john@agency.com",
                "Jane",
                "Smith",
                "+44999888777",
                "ACTIVE",
                List.of("AGENCY_ADMIN"),
                Instant.now()
        );

        given(userService.updateUser(eq(userId), any(UpdateUserRequest.class))).willReturn(updatedUser);

        mockMvc.perform(put("/api/v1/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.phone").value("+44999888777"));
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/v1/users/{id} should return 204")
    void deleteUser_ShouldReturn204() throws Exception {
        UUID userId = UUID.randomUUID();

        doNothing().when(userService).deleteUser(userId);

        mockMvc.perform(delete("/api/v1/users/{id}", userId))
                .andExpect(status().isNoContent());
    }
}
