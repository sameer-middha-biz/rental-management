package com.rental.pms.modules.tenant.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.event.DomainEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.modules.subscription.service.SubscriptionService;
import com.rental.pms.modules.tenant.dto.TenantRegistrationRequest;
import com.rental.pms.modules.tenant.entity.Tenant;
import com.rental.pms.modules.tenant.event.TenantRegisteredEvent;
import com.rental.pms.modules.tenant.repository.TenantRepository;
import com.rental.pms.modules.user.dto.AuthResponse;
import com.rental.pms.modules.user.entity.Role;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.repository.RefreshTokenRepository;
import com.rental.pms.modules.user.repository.RoleRepository;
import com.rental.pms.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantRegistrationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

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
    private SubscriptionService subscriptionService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @InjectMocks
    private TenantRegistrationService tenantRegistrationService;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    private TenantRegistrationRequest validRequest;
    private Role agencyAdminRole;

    @BeforeEach
    void setUp() {
        validRequest = new TenantRegistrationRequest(
                "admin@newagency.com", "SecurePass123!", "John", "Doe", "New Agency");

        agencyAdminRole = new Role();
        agencyAdminRole.setId(UUID.randomUUID());
        agencyAdminRole.setName("AGENCY_ADMIN");
        agencyAdminRole.setPermissions(new HashSet<>());
    }

    @Test
    void register_WithValidRequest_ShouldCreateTenantAndUser() {
        // Arrange
        when(userRepository.existsByEmail(validRequest.email())).thenReturn(false);
        when(tenantRepository.existsBySlug(anyString())).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(passwordEncoder.encode(validRequest.password())).thenReturn("$2a$12$encodedHash");
        when(roleRepository.findByName("AGENCY_ADMIN")).thenReturn(Optional.of(agencyAdminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any()))
                .thenReturn("access-token-dummy");
        when(jwtTokenProvider.generateRefreshToken(any()))
                .thenReturn("refresh-token-dummy");
        when(jwtTokenProvider.getAccessTokenExpiry())
                .thenReturn(Duration.ofMinutes(15));
        when(jwtTokenProvider.getRefreshTokenExpiry())
                .thenReturn(Duration.ofDays(7));

        // Act
        AuthResponse response = tenantRegistrationService.register(validRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token-dummy");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-dummy");
        assertThat(response.expiresIn()).isEqualTo(900L);

        verify(tenantRepository).save(any(Tenant.class));
        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void register_WithDuplicateEmail_ShouldThrowConflictException() {
        // Arrange
        when(userRepository.existsByEmail(validRequest.email())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> tenantRegistrationService.register(validRequest))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void register_ShouldPublishTenantRegisteredEvent() {
        // Arrange
        when(userRepository.existsByEmail(validRequest.email())).thenReturn(false);
        when(tenantRepository.existsBySlug(anyString())).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encodedHash");
        when(roleRepository.findByName("AGENCY_ADMIN")).thenReturn(Optional.of(agencyAdminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any()))
                .thenReturn("access-token-dummy");
        when(jwtTokenProvider.generateRefreshToken(any()))
                .thenReturn("refresh-token-dummy");
        when(jwtTokenProvider.getAccessTokenExpiry()).thenReturn(Duration.ofMinutes(15));
        when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(7));

        // Act
        tenantRegistrationService.register(validRequest);

        // Assert
        verify(domainEventPublisher, times(2)).publish(eventCaptor.capture());

        Object firstEvent = eventCaptor.getAllValues().get(0);
        assertThat(firstEvent).isInstanceOf(TenantRegisteredEvent.class);

        TenantRegisteredEvent tenantEvent = (TenantRegisteredEvent) firstEvent;
        assertThat(tenantEvent.tenantName()).isEqualTo("New Agency");
    }

    @Test
    void register_ShouldCallCreateStarterSubscription() {
        // Arrange
        UUID tenantId = UUID.randomUUID();
        when(userRepository.existsByEmail(validRequest.email())).thenReturn(false);
        when(tenantRepository.existsBySlug(anyString())).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant t = invocation.getArgument(0);
            t.setId(tenantId);
            return t;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encodedHash");
        when(roleRepository.findByName("AGENCY_ADMIN")).thenReturn(Optional.of(agencyAdminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any()))
                .thenReturn("access-token-dummy");
        when(jwtTokenProvider.generateRefreshToken(any()))
                .thenReturn("refresh-token-dummy");
        when(jwtTokenProvider.getAccessTokenExpiry()).thenReturn(Duration.ofMinutes(15));
        when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(7));

        // Act
        tenantRegistrationService.register(validRequest);

        // Assert
        verify(subscriptionService).createStarterSubscription(eq(tenantId));
    }
}
