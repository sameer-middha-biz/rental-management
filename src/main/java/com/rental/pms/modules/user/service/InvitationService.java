package com.rental.pms.modules.user.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.common.event.DomainEvent;
import com.rental.pms.common.event.DomainEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.TenantContext;
import com.rental.pms.modules.user.dto.AcceptInviteRequest;
import com.rental.pms.modules.user.dto.AuthResponse;
import com.rental.pms.modules.user.dto.InvitationResponse;
import com.rental.pms.modules.user.dto.InviteUserRequest;
import com.rental.pms.modules.user.entity.Invitation;
import com.rental.pms.modules.user.entity.InvitationStatus;
import com.rental.pms.modules.user.entity.Permission;
import com.rental.pms.modules.user.entity.RefreshToken;
import com.rental.pms.modules.user.entity.Role;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.entity.UserStatus;
import com.rental.pms.modules.user.event.InvitationCreatedEvent;
import com.rental.pms.modules.user.event.UserCreatedEvent;
import com.rental.pms.modules.user.exception.InvitationExpiredException;
import com.rental.pms.modules.user.repository.InvitationRepository;
import com.rental.pms.modules.user.repository.RefreshTokenRepository;
import com.rental.pms.modules.user.repository.RoleRepository;
import com.rental.pms.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditEventPublisher auditEventPublisher;
    private final CurrentUser currentUser;

    @Value("${pms.invitation.expiry-days:7}")
    private int expiryDays;

    @Transactional
    public InvitationResponse invite(InviteUserRequest request) {
        UUID tenantId = currentUser.getTenantId();

        if (userRepository.existsByEmailAndTenantId(request.email(), tenantId)) {
            throw new ConflictException("Email already registered in this tenant", "USER.EMAIL.DUPLICATE");
        }

        invitationRepository.findByEmailAndTenantIdAndStatus(
                request.email(), tenantId, InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new ConflictException("Pending invitation already exists for this email",
                            "USER.INVITATION.DUPLICATE");
                });

        Role role = roleRepository.findByName(request.roleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", request.roleName()));

        String token = UUID.randomUUID().toString();
        Invitation invitation = Invitation.builder()
                .email(request.email())
                .roleId(role.getId())
                .token(token)
                .invitedBy(currentUser.getUserId())
                .expiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS))
                .build();
        invitation = invitationRepository.save(invitation);

        domainEventPublisher.publish(new InvitationCreatedEvent(
                DomainEvent.now(tenantId),
                tenantId, invitation.getId(), request.email(), token, currentUser.getUserId()));

        // Debug-only — token in URL is sensitive; notification module (Phase 8) will send email
        log.debug("Invitation created: id={}, email={}", invitation.getId(), request.email());

        return toResponse(invitation, role.getName());
    }

    @Transactional
    public AuthResponse acceptInvite(String token, AcceptInviteRequest request) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(InvitationExpiredException::new);

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvitationExpiredException();
        }

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new InvitationExpiredException();
        }

        UUID tenantId = invitation.getTenantId();

        if (userRepository.existsByEmailAndTenantId(invitation.getEmail(), tenantId)) {
            throw new ConflictException("Email already registered in this tenant", "USER.EMAIL.DUPLICATE");
        }
        TenantContext.setTenantId(tenantId);
        try {
            Role role = roleRepository.findById(invitation.getRoleId())
                    .orElseThrow(() -> new IllegalStateException("Role not found for invitation"));

            User user = User.builder()
                    .email(invitation.getEmail())
                    .passwordHash(passwordEncoder.encode(request.password()))
                    .firstName(request.firstName())
                    .lastName(request.lastName())
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(role))
                    .build();
            user = userRepository.save(user);

            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitationRepository.save(invitation);

            // Generate tokens
            List<String> roles = List.of(role.getName());
            List<String> permissions = role.getPermissions().stream()
                    .map(Permission::getCode)
                    .toList();
            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(), tenantId, roles, permissions);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

            saveRefreshToken(user.getId(), refreshToken);

            domainEventPublisher.publish(new UserCreatedEvent(
                    DomainEvent.now(tenantId),
                    tenantId, user.getId(), user.getEmail(),
                    UserCreatedEvent.SOURCE_INVITATION));

            auditEventPublisher.publish(tenantId, user.getId(),
                    "USER_CREATED_VIA_INVITATION", "User", user.getId(),
                    "User created via invitation");

            return new AuthResponse(accessToken, refreshToken,
                    jwtTokenProvider.getAccessTokenExpiry().getSeconds());
        } finally {
            TenantContext.clear();
        }
    }

    public PageResponse<InvitationResponse> getInvitations(Pageable pageable) {
        UUID tenantId = currentUser.getTenantId();

        // Pre-load all roles into a map to avoid N+1 queries (only ~6 system roles exist)
        Map<UUID, String> roleNameMap = roleRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Role::getId, Role::getName));

        Page<InvitationResponse> page = invitationRepository.findAllByTenantId(tenantId, pageable)
                .map(inv -> {
                    String roleName = roleNameMap.getOrDefault(inv.getRoleId(), "UNKNOWN");
                    return toResponse(inv, roleName);
                });
        return PageResponse.from(page);
    }

    @Transactional
    public void revokeInvitation(UUID id) {
        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "id", id));

        invitation.setStatus(InvitationStatus.EXPIRED);
        invitationRepository.save(invitation);

        log.info("Invitation revoked: id={}, email={}", id, invitation.getEmail());
    }

    private InvitationResponse toResponse(Invitation invitation, String roleName) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                roleName,
                invitation.getStatus().name(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt()
        );
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
