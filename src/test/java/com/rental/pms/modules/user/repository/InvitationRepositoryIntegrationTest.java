package com.rental.pms.modules.user.repository;

import com.rental.pms.common.security.TenantContext;
import com.rental.pms.modules.tenant.entity.Tenant;
import com.rental.pms.modules.tenant.entity.TenantStatus;
import com.rental.pms.modules.tenant.repository.TenantRepository;
import com.rental.pms.modules.user.entity.Invitation;
import com.rental.pms.modules.user.entity.InvitationStatus;
import com.rental.pms.modules.user.entity.Role;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("InvitationRepository Integration Tests")
class InvitationRepositoryIntegrationTest {

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Tenant tenantA;
    private Tenant tenantB;
    private User invitingUser;
    private Role agencyAdminRole;

    @BeforeEach
    void setUp() {
        tenantA = Tenant.builder()
                .name("Invite Agency A")
                .slug("invite-agency-a-" + UUID.randomUUID().toString().substring(0, 8))
                .contactEmail("admin@invite-a.com")
                .timezone("UTC")
                .defaultCurrency("GBP")
                .status(TenantStatus.ACTIVE)
                .build();
        tenantA = tenantRepository.saveAndFlush(tenantA);

        tenantB = Tenant.builder()
                .name("Invite Agency B")
                .slug("invite-agency-b-" + UUID.randomUUID().toString().substring(0, 8))
                .contactEmail("admin@invite-b.com")
                .timezone("UTC")
                .defaultCurrency("GBP")
                .status(TenantStatus.ACTIVE)
                .build();
        tenantB = tenantRepository.saveAndFlush(tenantB);

        agencyAdminRole = roleRepository.findByName("AGENCY_ADMIN")
                .orElseThrow(() -> new IllegalStateException("AGENCY_ADMIN role not found; Flyway seed may not have run."));

        // Create the inviting user under Tenant A
        TenantContext.setTenantId(tenantA.getId());
        invitingUser = User.builder()
                .email("inviter@invite-a.com")
                .passwordHash("$2a$12$hashedPassword")
                .firstName("Inviter")
                .lastName("Admin")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(agencyAdminRole))
                .build();
        invitingUser = userRepository.saveAndFlush(invitingUser);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        disableTenantFilter();
    }

    private Invitation createInvitationForTenant(UUID tenantId, String email, String token) {
        TenantContext.setTenantId(tenantId);

        Invitation invitation = Invitation.builder()
                .email(email)
                .roleId(agencyAdminRole.getId())
                .token(token)
                .invitedBy(invitingUser.getId())
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        return invitationRepository.saveAndFlush(invitation);
    }

    private void enableTenantFilter(UUID tenantId) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    }

    private void disableTenantFilter() {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
    }

    @Test
    @DisplayName("findByToken should return invitation when token matches")
    void findByToken_ShouldReturnInvitation() {
        String token = "invite-token-" + UUID.randomUUID();
        Invitation saved = createInvitationForTenant(tenantA.getId(), "newuser@agency.com", token);

        Optional<Invitation> found = invitationRepository.findByToken(token);

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("newuser@agency.com");
        assertThat(found.get().getToken()).isEqualTo(token);
        assertThat(found.get().getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(found.get().getRoleId()).isEqualTo(agencyAdminRole.getId());
        assertThat(found.get().getInvitedBy()).isEqualTo(invitingUser.getId());
        assertThat(found.get().getTenantId()).isEqualTo(tenantA.getId());
    }

    @Test
    @DisplayName("findByToken should return empty when token does not match")
    void findByToken_WhenNotFound_ShouldReturnEmpty() {
        createInvitationForTenant(tenantA.getId(), "someone@agency.com", "real-token");

        Optional<Invitation> found = invitationRepository.findByToken("nonexistent-token");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findAll with tenant filter should not return invitations from different tenant (tenant isolation)")
    void findAll_WhenDifferentTenant_ShouldReturnEmpty() {
        // Create invitation under Tenant A
        createInvitationForTenant(tenantA.getId(), "invite@agency-a.com", "token-a-" + UUID.randomUUID());

        // Clear persistence context
        entityManager.flush();
        entityManager.clear();

        // Enable Hibernate tenant filter for Tenant B
        enableTenantFilter(tenantB.getId());

        // Query should return empty because invitation belongs to Tenant A
        var invitations = invitationRepository.findAll();

        assertThat(invitations).isEmpty();
    }

    @Test
    @DisplayName("findAll with tenant filter should return invitations from same tenant")
    void findAll_WhenSameTenant_ShouldReturnInvitations() {
        createInvitationForTenant(tenantA.getId(), "invite1@agency-a.com", "token-1-" + UUID.randomUUID());
        createInvitationForTenant(tenantA.getId(), "invite2@agency-a.com", "token-2-" + UUID.randomUUID());

        entityManager.flush();
        entityManager.clear();

        enableTenantFilter(tenantA.getId());

        var invitations = invitationRepository.findAll();

        assertThat(invitations).hasSize(2);
        assertThat(invitations)
                .extracting(Invitation::getEmail)
                .containsExactlyInAnyOrder("invite1@agency-a.com", "invite2@agency-a.com");
    }

    @Test
    @DisplayName("findByEmailAndTenantIdAndStatus should return matching invitation")
    void findByEmailAndTenantIdAndStatus_ShouldReturnInvitation() {
        createInvitationForTenant(tenantA.getId(), "pending@agency-a.com", "token-pending-" + UUID.randomUUID());

        Optional<Invitation> found = invitationRepository.findByEmailAndTenantIdAndStatus(
                "pending@agency-a.com", tenantA.getId(), InvitationStatus.PENDING
        );

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("pending@agency-a.com");
        assertThat(found.get().getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    @DisplayName("save should set audit timestamps and tenant ID via TenantInterceptor")
    void save_ShouldSetAuditTimestampsAndTenantId() {
        Invitation saved = createInvitationForTenant(tenantA.getId(), "audit@agency-a.com", "token-audit-" + UUID.randomUUID());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(tenantA.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }
}
