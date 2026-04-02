package com.rental.pms.modules.user.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.modules.user.dto.AuthResponse;
import com.rental.pms.modules.user.dto.LoginRequest;
import com.rental.pms.modules.user.dto.RefreshTokenRequest;
import com.rental.pms.modules.user.entity.RefreshToken;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.entity.UserStatus;
import com.rental.pms.modules.user.exception.InvalidCredentialsException;
import com.rental.pms.modules.user.exception.TokenInvalidException;
import com.rental.pms.modules.user.exception.UserDisabledException;
import com.rental.pms.modules.user.fixture.TestUserBuilder;
import com.rental.pms.modules.user.repository.RefreshTokenRepository;
import com.rental.pms.modules.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.times;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @InjectMocks
    private AuthService authService;

    private User activeUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        activeUser = TestUserBuilder.aUser()
                .withEmail("user@test.com")
                .withPasswordHash("$2a$12$encodedHash")
                .withStatus(UserStatus.ACTIVE)
                .build();

        loginRequest = new LoginRequest("user@test.com", "password123");
    }

    @Test
    void login_WithValidCredentials_ShouldReturnTokens() {
        // Arrange
        when(userRepository.findByEmailWithRolesAndPermissions(loginRequest.email()))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(loginRequest.password(), activeUser.getPasswordHash()))
                .thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any()))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any()))
                .thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpiry())
                .thenReturn(Duration.ofMinutes(15));
        when(jwtTokenProvider.getRefreshTokenExpiry())
                .thenReturn(Duration.ofDays(7));
        when(userRepository.save(any(User.class))).thenReturn(activeUser);

        // Act
        AuthResponse response = authService.login(loginRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(900L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_WithWrongPassword_ShouldThrowInvalidCredentialsException() {
        // Arrange
        when(userRepository.findByEmailWithRolesAndPermissions(loginRequest.email()))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(loginRequest.password(), activeUser.getPasswordHash()))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_WithNonexistentEmail_ShouldThrowInvalidCredentialsException() {
        // Arrange
        when(userRepository.findByEmailWithRolesAndPermissions(loginRequest.email()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_WithDisabledUser_ShouldThrowUserDisabledException() {
        // Arrange
        User disabledUser = TestUserBuilder.aUser()
                .withEmail("user@test.com")
                .withStatus(UserStatus.DISABLED)
                .build();

        when(userRepository.findByEmailWithRolesAndPermissions(loginRequest.email()))
                .thenReturn(Optional.of(disabledUser));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UserDisabledException.class);
    }

    @Test
    void refreshToken_WithValidToken_ShouldReturnNewTokenPair() {
        // Arrange
        String rawRefreshToken = "valid-refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawRefreshToken);
        UUID userId = activeUser.getId();

        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("refresh");

        when(jwtTokenProvider.validateAndExtractClaims(rawRefreshToken)).thenReturn(claims);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(userId);

        String tokenHash = AuthService.hashToken(rawRefreshToken);
        RefreshToken storedToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(storedToken));
        when(userRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(activeUser));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any()))
                .thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(any()))
                .thenReturn("new-refresh-token");
        when(jwtTokenProvider.getAccessTokenExpiry()).thenReturn(Duration.ofMinutes(15));
        when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(7));

        // Act
        AuthResponse response = authService.refreshToken(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(storedToken.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refreshToken_WithRevokedToken_ShouldThrowTokenInvalidException() {
        // Arrange
        String rawRefreshToken = "revoked-refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawRefreshToken);

        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("refresh");

        when(jwtTokenProvider.validateAndExtractClaims(rawRefreshToken)).thenReturn(claims);

        String tokenHash = AuthService.hashToken(rawRefreshToken);
        RefreshToken revokedToken = RefreshToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedToken));

        // Act & Assert
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(TokenInvalidException.class);
    }

    @Test
    void logout_ShouldRevokeRefreshToken() {
        // Arrange
        String rawRefreshToken = "token-to-revoke";
        RefreshTokenRequest request = new RefreshTokenRequest(rawRefreshToken);

        String tokenHash = AuthService.hashToken(rawRefreshToken);
        RefreshToken storedToken = RefreshToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(storedToken));

        // Act
        authService.logout(request);

        // Assert
        assertThat(storedToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(storedToken);
    }
}
