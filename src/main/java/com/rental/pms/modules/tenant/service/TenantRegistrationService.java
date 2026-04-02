package com.rental.pms.modules.tenant.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.event.DomainEvent;
import com.rental.pms.common.event.DomainEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.TenantContext;
import com.rental.pms.common.util.SlugGenerator;
import com.rental.pms.modules.subscription.service.SubscriptionService;
import com.rental.pms.modules.tenant.dto.TenantRegistrationRequest;
import com.rental.pms.modules.tenant.entity.Tenant;
import com.rental.pms.modules.tenant.event.TenantRegisteredEvent;
import com.rental.pms.modules.tenant.repository.TenantRepository;
import com.rental.pms.modules.user.dto.AuthResponse;
import com.rental.pms.modules.user.entity.RefreshToken;
import com.rental.pms.modules.user.entity.Role;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.entity.UserStatus;
import com.rental.pms.modules.user.event.UserCreatedEvent;
import com.rental.pms.modules.user.repository.RefreshTokenRepository;
import com.rental.pms.modules.user.repository.RoleRepository;
import com.rental.pms.modules.user.repository.UserRepository;
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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SubscriptionService subscriptionService;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditEventPublisher auditEventPublisher;

    @Transactional
    public AuthResponse register(TenantRegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered", "AUTH.EMAIL.DUPLICATE");
        }

        // Create tenant
        String slug = generateUniqueSlug(request.agencyName());
        Tenant tenant = Tenant.builder()
                .name(request.agencyName())
                .slug(slug)
                .contactEmail(request.email())
                .build();
        tenant = tenantRepository.save(tenant);

        // Set TenantContext for BaseEntity's TenantInterceptor
        TenantContext.setTenantId(tenant.getId());
        try {
            // Create admin user
            User user = User.builder()
                    .email(request.email())
                    .passwordHash(passwordEncoder.encode(request.password()))
                    .firstName(request.firstName())
                    .lastName(request.lastName())
                    .status(UserStatus.ACTIVE)
                    .build();

            Role agencyAdmin = roleRepository.findByName("AGENCY_ADMIN")
                    .orElseThrow(() -> new IllegalStateException("AGENCY_ADMIN role not found in database"));
            user.setRoles(Set.of(agencyAdmin));
            user = userRepository.save(user);

            // Create starter subscription (stub in Phase 3)
            subscriptionService.createStarterSubscription(tenant.getId());

            // Generate tokens
            List<String> roles = List.of("AGENCY_ADMIN");
            List<String> permissions = agencyAdmin.getPermissions().stream()
                    .map(p -> p.getCode())
                    .toList();
            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(), tenant.getId(), roles, permissions);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

            // Save refresh token
            saveRefreshToken(user.getId(), refreshToken);

            // Publish events
            domainEventPublisher.publish(new TenantRegisteredEvent(
                    DomainEvent.now(tenant.getId()),
                    tenant.getId(), tenant.getName(), tenant.getSlug(), user.getId()));
            domainEventPublisher.publish(new UserCreatedEvent(
                    DomainEvent.now(tenant.getId()),
                    tenant.getId(), user.getId(), user.getEmail(),
                    UserCreatedEvent.SOURCE_REGISTRATION));

            auditEventPublisher.publish(tenant.getId(), user.getId(),
                    "TENANT_REGISTERED", "Tenant", tenant.getId(),
                    "Tenant registered: " + tenant.getName());

            log.info("Tenant registered: name={}, slug={}, adminEmail={}",
                    tenant.getName(), tenant.getSlug(), user.getEmail());

            return new AuthResponse(accessToken, refreshToken,
                    jwtTokenProvider.getAccessTokenExpiry().getSeconds());
        } finally {
            TenantContext.clear();
        }
    }

    private String generateUniqueSlug(String name) {
        String baseSlug = SlugGenerator.generateSlug(name);
        String slug = baseSlug;
        int suffix = 1;
        int maxAttempts = 1000;
        while (tenantRepository.existsBySlug(slug)) {
            if (suffix > maxAttempts) {
                throw new IllegalStateException(
                        "Unable to generate unique slug after " + maxAttempts + " attempts for: " + baseSlug);
            }
            slug = baseSlug + "-" + suffix;
            suffix++;
        }
        return slug;
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
