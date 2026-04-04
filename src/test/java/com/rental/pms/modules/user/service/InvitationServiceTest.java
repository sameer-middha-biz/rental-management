package com.rental.pms.modules.user.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.event.DomainEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.modules.user.dto.AcceptInviteRequest;
import com.rental.pms.modules.user.dto.AuthResponse;
import com.rental.pms.modules.user.dto.InvitationResponse;
import com.rental.pms.modules.user.dto.InviteUserRequest;
import com.rental.pms.modules.user.entity.Invitation;
import com.rental.pms.modules.user.entity.InvitationStatus;
import com.rental.pms.modules.user.entity.Role;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.exception.InvitationExpiredException;
import com.rental.pms.modules.user.repository.InvitationRepository;
import com.rental.pms.modules.user.repository.RefreshTokenRepository;
import com.rental.pms.modules.user.repository.RoleRepository;
import com.rental.pms.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private InvitationService invitationService;

    private UUID tenantId;
    private UUID currentUserId;
    private Role propertyManagerRole;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();
        ReflectionTestUtils.setField(invitationService, "expiryDays", 7);

        propertyManagerRole = new Role();
        propertyManagerRole.setId(UUID.randomUUID());
        propertyManagerRole.setName("PROPERTY_MANAGER");
        propertyManagerRole.setPermissions(new HashSet<>());
    }

    @Test
    void invite_WithValidRequest_ShouldCreateInvitation() {
        // Arrange
        InviteUserRequest request = new InviteUserRequest("new@test.com", "PROPERTY_MANAGER");

        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(currentUser.getUserId()).thenReturn(currentUserId);
        when(userRepository.existsByEmailAndTenantId(request.email(), tenantId)).thenReturn(false);
        when(invitationRepository.findByEmailAndTenantIdAndStatus(
                request.email(), tenantId, InvitationStatus.PENDING))
                .thenReturn(Optional.empty());
        when(roleRepository.findByName("PROPERTY_MANAGER"))
                .thenReturn(Optional.of(propertyManagerRole));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> {
            Invitation inv = invocation.getArgument(0);
            inv.setId(UUID.randomUUID());
            inv.setTenantId(tenantId);
            return inv;
        });

        // Act
        InvitationResponse response = invitationService.invite(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("new@test.com");
        assertThat(response.roleName()).isEqualTo("PROPERTY_MANAGER");
        verify(invitationRepository).save(any(Invitation.class));
        verify(domainEventPublisher).publish(any());
    }

    @Test
    void invite_WithExistingEmail_ShouldThrowConflictException() {
        // Arrange
        InviteUserRequest request = new InviteUserRequest("existing@test.com", "PROPERTY_MANAGER");

        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(userRepository.existsByEmailAndTenantId(request.email(), tenantId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> invitationService.invite(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void invite_WithPendingDuplicate_ShouldThrowConflictException() {
        // Arrange
        InviteUserRequest request = new InviteUserRequest("pending@test.com", "PROPERTY_MANAGER");

        Invitation existingInvitation = Invitation.builder()
                .email("pending@test.com")
                .status(InvitationStatus.PENDING)
                .build();

        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(userRepository.existsByEmailAndTenantId(request.email(), tenantId)).thenReturn(false);
        when(invitationRepository.findByEmailAndTenantIdAndStatus(
                request.email(), tenantId, InvitationStatus.PENDING))
                .thenReturn(Optional.of(existingInvitation));

        // Act & Assert
        assertThatThrownBy(() -> invitationService.invite(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptInvite_WithValidToken_ShouldCreateUserAndReturnTokens() {
        // Arrange
        String token = UUID.randomUUID().toString();
        AcceptInviteRequest request = new AcceptInviteRequest("NewPass123!", "Jane", "Doe");

        Invitation invitation = Invitation.builder()
                .email("new@test.com")
                .roleId(propertyManagerRole.getId())
                .token(token)
                .invitedBy(currentUserId)
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        invitation.setId(UUID.randomUUID());
        invitation.setTenantId(tenantId);

        when(invitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));
        when(userRepository.existsByEmailAndTenantId("new@test.com", tenantId)).thenReturn(false);
        when(roleRepository.findById(propertyManagerRole.getId()))
                .thenReturn(Optional.of(propertyManagerRole));
        when(passwordEncoder.encode(request.password())).thenReturn("$2a$12$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(invitationRepository.save(any(Invitation.class))).thenReturn(invitation);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any()))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any()))
                .thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpiry()).thenReturn(Duration.ofMinutes(15));
        when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(7));

        // Act
        AuthResponse response = invitationService.acceptInvite(token, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void acceptInvite_WithExpiredToken_ShouldThrowInvitationExpiredException() {
        // Arrange
        String token = UUID.randomUUID().toString();
        AcceptInviteRequest request = new AcceptInviteRequest("NewPass123!", "Jane", "Doe");

        Invitation invitation = Invitation.builder()
                .email("new@test.com")
                .roleId(propertyManagerRole.getId())
                .token(token)
                .invitedBy(currentUserId)
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        invitation.setId(UUID.randomUUID());
        invitation.setTenantId(tenantId);

        when(invitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));

        // Act & Assert
        assertThatThrownBy(() -> invitationService.acceptInvite(token, request))
                .isInstanceOf(InvitationExpiredException.class);
    }

    @Test
    void revokeInvitation_ShouldSetStatusToExpired() {
        // Arrange
        UUID invitationId = UUID.randomUUID();
        Invitation invitation = Invitation.builder()
                .email("revoke@test.com")
                .status(InvitationStatus.PENDING)
                .build();
        invitation.setId(invitationId);

        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));

        // Act
        invitationService.revokeInvitation(invitationId);

        // Assert
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        verify(invitationRepository).save(invitation);
    }
}
