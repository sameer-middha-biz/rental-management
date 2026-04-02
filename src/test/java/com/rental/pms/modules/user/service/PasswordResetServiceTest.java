package com.rental.pms.modules.user.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.modules.user.dto.PasswordResetConfirmRequest;
import com.rental.pms.modules.user.dto.PasswordResetRequest;
import com.rental.pms.modules.user.entity.PasswordResetToken;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.exception.TokenExpiredException;
import com.rental.pms.modules.user.exception.TokenInvalidException;
import com.rental.pms.modules.user.fixture.TestUserBuilder;
import com.rental.pms.modules.user.repository.PasswordResetTokenRepository;
import com.rental.pms.modules.user.repository.RefreshTokenRepository;
import com.rental.pms.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "expiryHours", 1);

        testUser = TestUserBuilder.aUser()
                .withEmail("user@test.com")
                .build();
    }

    @Test
    void requestPasswordReset_WithExistingEmail_ShouldCreateToken() {
        // Arrange
        PasswordResetRequest request = new PasswordResetRequest("user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(testUser));

        // Act
        passwordResetService.requestPasswordReset(request);

        // Assert
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void requestPasswordReset_WithUnknownEmail_ShouldSilentlyReturn() {
        // Arrange
        PasswordResetRequest request = new PasswordResetRequest("unknown@test.com");
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        // Act
        passwordResetService.requestPasswordReset(request);

        // Assert
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void confirmPasswordReset_WithValidToken_ShouldUpdatePassword() {
        // Arrange
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = PasswordResetService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(testUser.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .used(false)
                .build();

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(rawToken, "NewSecurePass123!");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("NewSecurePass123!")).thenReturn("$2a$12$newEncodedHash");

        // Act
        passwordResetService.confirmPasswordReset(request);

        // Assert
        assertThat(testUser.getPasswordHash()).isEqualTo("$2a$12$newEncodedHash");
        assertThat(resetToken.isUsed()).isTrue();
        verify(userRepository).save(testUser);
        verify(passwordResetTokenRepository).save(resetToken);
        verify(refreshTokenRepository).revokeAllByUserId(testUser.getId());
    }

    @Test
    void confirmPasswordReset_WithExpiredToken_ShouldThrowTokenExpiredException() {
        // Arrange
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = PasswordResetService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(testUser.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .used(false)
                .build();

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(rawToken, "NewSecurePass123!");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act & Assert
        assertThatThrownBy(() -> passwordResetService.confirmPasswordReset(request))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    void confirmPasswordReset_WithUsedToken_ShouldThrowTokenInvalidException() {
        // Arrange
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = PasswordResetService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(testUser.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .used(true)
                .build();

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(rawToken, "NewSecurePass123!");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        // Act & Assert
        assertThatThrownBy(() -> passwordResetService.confirmPasswordReset(request))
                .isInstanceOf(TokenInvalidException.class);
    }
}
