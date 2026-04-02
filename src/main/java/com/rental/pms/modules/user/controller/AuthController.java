package com.rental.pms.modules.user.controller;

import com.rental.pms.modules.tenant.dto.TenantRegistrationRequest;
import com.rental.pms.modules.tenant.service.TenantRegistrationService;
import com.rental.pms.modules.user.dto.AuthResponse;
import com.rental.pms.modules.user.dto.LoginRequest;
import com.rental.pms.modules.user.dto.PasswordResetConfirmRequest;
import com.rental.pms.modules.user.dto.PasswordResetRequest;
import com.rental.pms.modules.user.dto.RefreshTokenRequest;
import com.rental.pms.modules.user.service.AuthService;
import com.rental.pms.modules.user.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token refresh, logout, password reset")
public class AuthController {

    private final TenantRegistrationService tenantRegistrationService;
    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    @Operation(summary = "Register a new account (creates tenant + admin user)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody TenantRegistrationRequest request) {
        AuthResponse response = tenantRegistrationService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/users/me")).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Logout and invalidate refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    @Operation(summary = "Request password reset email")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Reset password with token")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmPasswordReset(request);
        return ResponseEntity.ok().build();
    }
}
