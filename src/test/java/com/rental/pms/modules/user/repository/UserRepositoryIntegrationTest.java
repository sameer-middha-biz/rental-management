package com.rental.pms.modules.user.repository;

import com.rental.pms.common.security.TenantContext;
import com.rental.pms.modules.tenant.entity.Tenant;
import com.rental.pms.modules.tenant.entity.TenantStatus;
import com.rental.pms.modules.tenant.repository.TenantRepository;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserRepository Integration Tests")
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Tenant tenantA;
    private Tenant tenantB;
    private Role agencyAdminRole;

    @BeforeEach
    void setUp() {
        tenantA = Tenant.builder()
                .name("Agency A")
                .slug("agency-a-" + UUID.randomUUID().toString().substring(0, 8))
                .contactEmail("admin@agency-a.com")
                .timezone("UTC")
                .defaultCurrency("GBP")
                .status(TenantStatus.ACTIVE)
                .build();
        tenantA = tenantRepository.saveAndFlush(tenantA);

        tenantB = Tenant.builder()
                .name("Agency B")
                .slug("agency-b-" + UUID.randomUUID().toString().substring(0, 8))
                .contactEmail("admin@agency-b.com")
                .timezone("UTC")
                .defaultCurrency("GBP")
                .status(TenantStatus.ACTIVE)
                .build();
        tenantB = tenantRepository.saveAndFlush(tenantB);

        // Roles are seeded by Flyway V2.6
        agencyAdminRole = roleRepository.findByName("AGENCY_ADMIN")
                .orElseThrow(() -> new IllegalStateException("AGENCY_ADMIN role not found; Flyway seed may not have run."));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private User createUserForTenant(UUID tenantId, String email) {
        TenantContext.setTenantId(tenantId);

        User user = User.builder()
                .email(email)
                .passwordHash("$2a$12$hashedPassword")
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(agencyAdminRole))
                .build();

        return userRepository.saveAndFlush(user);
    }

    private void enableTenantFilter(UUID tenantId) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    }

    @Test
    @DisplayName("findByEmail should return user when email matches")
    void findByEmail_ShouldReturnUser() {
        User savedUser = createUserForTenant(tenantA.getId(), "john@agency-a.com");

        Optional<User> found = userRepository.findByEmail("john@agency-a.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("john@agency-a.com");
        assertThat(found.get().getFirstName()).isEqualTo("Test");
        assertThat(found.get().getTenantId()).isEqualTo(tenantA.getId());
    }

    @Test
    @DisplayName("findByEmail should return empty when email does not match")
    void findByEmail_WhenNotFound_ShouldReturnEmpty() {
        createUserForTenant(tenantA.getId(), "existing@agency-a.com");

        Optional<User> found = userRepository.findByEmail("nonexistent@agency.com");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByEmailWithRolesAndPermissions should fetch roles and permissions eagerly")
    void findByEmailWithRolesAndPermissions_ShouldFetchRolesAndPermissions() {
        User savedUser = createUserForTenant(tenantA.getId(), "admin@agency-a.com");

        // Clear persistence context to force fresh load
        entityManager.clear();

        Optional<User> found = userRepository.findByEmailWithRolesAndPermissions("admin@agency-a.com");

        assertThat(found).isPresent();
        assertThat(found.get().getRoles()).isNotEmpty();
        assertThat(found.get().getRoles())
                .extracting(Role::getName)
                .contains("AGENCY_ADMIN");

        // Verify permissions are loaded (AGENCY_ADMIN should have many permissions per seed data)
        Role role = found.get().getRoles().iterator().next();
        assertThat(role.getPermissions()).isNotEmpty();
    }

    @Test
    @DisplayName("findAll with tenant filter should not return users from different tenant (tenant isolation)")
    void findAll_WhenDifferentTenant_ShouldReturnEmpty() {
        // Create a user under Tenant A
        createUserForTenant(tenantA.getId(), "usera@agency-a.com");

        // Clear persistence context
        entityManager.flush();
        entityManager.clear();

        // Enable Hibernate tenant filter for Tenant B
        enableTenantFilter(tenantB.getId());

        // Query should return empty because user belongs to Tenant A
        var users = userRepository.findAll();

        assertThat(users).isEmpty();
    }

    @Test
    @DisplayName("findAll with tenant filter should return users from same tenant")
    void findAll_WhenSameTenant_ShouldReturnUsers() {
        createUserForTenant(tenantA.getId(), "usera@agency-a.com");

        entityManager.flush();
        entityManager.clear();

        // Enable Hibernate tenant filter for Tenant A
        enableTenantFilter(tenantA.getId());

        var users = userRepository.findAll();

        assertThat(users).hasSize(1);
        assertThat(users.get(0).getEmail()).isEqualTo("usera@agency-a.com");
    }

    @Test
    @DisplayName("existsByEmail should return true when email exists")
    void existsByEmail_WhenExists_ShouldReturnTrue() {
        createUserForTenant(tenantA.getId(), "check@agency-a.com");

        boolean exists = userRepository.existsByEmail("check@agency-a.com");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByEmail should return false when email does not exist")
    void existsByEmail_WhenNotExists_ShouldReturnFalse() {
        boolean exists = userRepository.existsByEmail("nobody@agency.com");

        assertThat(exists).isFalse();
    }
}
