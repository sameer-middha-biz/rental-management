package com.rental.pms.modules.user.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.modules.user.dto.AuthResponse;
import com.rental.pms.modules.user.dto.LoginRequest;
import com.rental.pms.modules.user.dto.RefreshTokenRequest;
import com.rental.pms.modules.user.entity.Permission;
import com.rental.pms.modules.user.entity.RefreshToken;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.entity.UserStatus;
import com.rental.pms.modules.user.exception.InvalidCredentialsException;
import com.rental.pms.modules.user.exception.TokenExpiredException;
import com.rental.pms.modules.user.exception.TokenInvalidException;
import com.rental.pms.modules.user.exception.UserDisabledException;
import com.rental.pms.modules.user.repository.RefreshTokenRepository;
import com.rental.pms.modules.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditEventPublisher auditEventPublisher;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailWithRolesAndPermissions(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new UserDisabledException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // Collect roles and permissions
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .toList();

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getTenantId(), roles, permissions);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // Save refresh token
        saveRefreshToken(user.getId(), refreshToken);

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditEventPublisher.publish(user.getTenantId(), user.getId(),
                "LOGIN_SUCCESS", "User", user.getId(), "User logged in");

        return new AuthResponse(accessToken, refreshToken,
                jwtTokenProvider.getAccessTokenExpiry().getSeconds());
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        // Validate JWT structure of the refresh token
        Claims claims;
        try {
            claims = jwtTokenProvider.validateAndExtractClaims(request.refreshToken());
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("Refresh");
        } catch (JwtException e) {
            throw new TokenInvalidException("Refresh");
        }

        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new TokenInvalidException("Refresh");
        }

        // Look up stored token
        String tokenHash = hashToken(request.refreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenInvalidException("Refresh"));

        if (storedToken.isRevoked()) {
            throw new TokenInvalidException("Refresh");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenExpiredException("Refresh");
        }

        // Revoke old token
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // Load user and check status
        UUID userId = jwtTokenProvider.getUserId(claims);
        User user = userRepository.findByIdWithRolesAndPermissions(userId)
                .orElseThrow(() -> new TokenInvalidException("Refresh"));

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new UserDisabledException();
        }

        // Generate new token pair
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .toList();

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getTenantId(), roles, permissions);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        saveRefreshToken(user.getId(), newRefreshToken);

        return new AuthResponse(newAccessToken, newRefreshToken,
                jwtTokenProvider.getAccessTokenExpiry().getSeconds());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private void saveRefreshToken(UUID userId, String rawToken) {
        String tokenHash = hashToken(rawToken);
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(jwtTokenProvider.getRefreshTokenExpiry()))
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
