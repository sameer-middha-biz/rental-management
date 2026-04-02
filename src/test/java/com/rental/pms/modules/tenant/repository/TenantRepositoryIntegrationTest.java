package com.rental.pms.modules.tenant.repository;

import com.rental.pms.modules.tenant.entity.Tenant;
import com.rental.pms.modules.tenant.entity.TenantStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TenantRepository Integration Tests")
class TenantRepositoryIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant createAndSaveTenant(String name, String slug) {
        Tenant tenant = Tenant.builder()
                .name(name)
                .slug(slug)
                .contactEmail("admin@" + slug + ".com")
                .timezone("UTC")
                .defaultCurrency("GBP")
                .status(TenantStatus.ACTIVE)
                .build();
        return tenantRepository.saveAndFlush(tenant);
    }

    @Test
    @DisplayName("findBySlug should return tenant when slug matches")
    void findBySlug_ShouldReturnTenant() {
        String uniqueSlug = "test-agency-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant savedTenant = createAndSaveTenant("Test Agency", uniqueSlug);

        Optional<Tenant> found = tenantRepository.findBySlug(uniqueSlug);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Agency");
        assertThat(found.get().getSlug()).isEqualTo(uniqueSlug);
        assertThat(found.get().getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(found.get().getId()).isEqualTo(savedTenant.getId());
    }

    @Test
    @DisplayName("findBySlug should return empty when slug does not match")
    void findBySlug_WhenNotFound_ShouldReturnEmpty() {
        Optional<Tenant> found = tenantRepository.findBySlug("nonexistent-slug");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsBySlug should return true when slug exists")
    void existsBySlug_WhenExists_ShouldReturnTrue() {
        String uniqueSlug = "existing-agency-" + UUID.randomUUID().toString().substring(0, 8);
        createAndSaveTenant("Existing Agency", uniqueSlug);

        boolean exists = tenantRepository.existsBySlug(uniqueSlug);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsBySlug should return false when slug does not exist")
    void existsBySlug_WhenNotExists_ShouldReturnFalse() {
        boolean exists = tenantRepository.existsBySlug("nonexistent-slug-" + UUID.randomUUID());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByContactEmail should return true when contact email exists")
    void existsByContactEmail_WhenExists_ShouldReturnTrue() {
        String uniqueSlug = "email-test-" + UUID.randomUUID().toString().substring(0, 8);
        createAndSaveTenant("Email Test Agency", uniqueSlug);

        boolean exists = tenantRepository.existsByContactEmail("admin@" + uniqueSlug + ".com");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("findAllByStatus should filter tenants by status")
    void findAllByStatus_ShouldFilterByStatus() {
        String activeSlug = "active-agency-" + UUID.randomUUID().toString().substring(0, 8);
        String suspendedSlug = "suspended-agency-" + UUID.randomUUID().toString().substring(0, 8);

        createAndSaveTenant("Active Agency", activeSlug);

        Tenant suspended = Tenant.builder()
                .name("Suspended Agency")
                .slug(suspendedSlug)
                .contactEmail("admin@" + suspendedSlug + ".com")
                .timezone("UTC")
                .defaultCurrency("GBP")
                .status(TenantStatus.SUSPENDED)
                .build();
        tenantRepository.saveAndFlush(suspended);

        var activePage = tenantRepository.findAllByStatus(
                TenantStatus.ACTIVE, org.springframework.data.domain.Pageable.unpaged());

        assertThat(activePage.getContent())
                .extracting(Tenant::getSlug)
                .contains(activeSlug)
                .doesNotContain(suspendedSlug);
    }

    @Test
    @DisplayName("save should persist tenant with audit timestamps")
    void save_ShouldPersistTenantWithTimestamps() {
        String uniqueSlug = "timestamp-test-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant saved = createAndSaveTenant("Timestamp Agency", uniqueSlug);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
    }
}
