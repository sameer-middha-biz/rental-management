package com.rental.pms.modules.user.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.modules.user.dto.PasswordResetConfirmRequest;
import com.rental.pms.modules.user.dto.PasswordResetRequest;
import com.rental.pms.modules.user.entity.PasswordResetToken;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.exception.TokenExpiredException;
import com.rental.pms.modules.user.exception.TokenInvalidException;
import com.rental.pms.modules.user.repository.PasswordResetTokenRepository;
import com.rental.pms.modules.user.repository.RefreshTokenRepository;
import com.rental.pms.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventPublisher auditEventPublisher;

    @Value("${pms.password-reset.expiry-hours:1}")
    private int expiryHours;

    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String rawToken = UUID.randomUUID().toString();
            String tokenHash = hashToken(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .userId(user.getId())
                    .tokenHash(tokenHash)
                    .expiresAt(Instant.now().plus(expiryHours, ChronoUnit.HOURS))
                    .build();
            passwordResetTokenRepository.save(resetToken);

            // Debug-only — notification module (Phase 8) will send email; never log tokens at INFO
            log.debug("Password reset token generated for userId={}", user.getId());
        });
        // Always return silently — don't reveal email existence
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        String tokenHash = hashToken(request.token());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenInvalidException("Password reset"));

        if (resetToken.isUsed()) {
            throw new TokenInvalidException("Password reset");
        }

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenExpiredException("Password reset");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new TokenInvalidException("Password reset"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Revoke all refresh tokens — force re-login
        refreshTokenRepository.revokeAllByUserId(user.getId());

        auditEventPublisher.publish(user.getTenantId(), user.getId(),
                "PASSWORD_RESET", "User", user.getId(), "Password reset completed");

        log.info("Password reset completed for userId={}", user.getId());
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
