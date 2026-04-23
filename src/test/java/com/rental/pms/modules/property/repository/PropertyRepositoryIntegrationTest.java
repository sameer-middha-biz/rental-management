package com.rental.pms.modules.property.repository;

import com.rental.pms.common.security.TenantContext;
import com.rental.pms.modules.property.entity.Property;
import com.rental.pms.modules.property.entity.PropertyStatus;
import com.rental.pms.modules.property.entity.PropertyType;
import com.rental.pms.modules.tenant.entity.Tenant;
import com.rental.pms.modules.tenant.entity.TenantStatus;
import com.rental.pms.modules.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PropertyRepository Integration Tests")
class PropertyRepositoryIntegrationTest {

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private EntityManager entityManager;

    private Tenant tenantA;
    private Tenant tenantB;

    @BeforeEach
    void setUp() {
        tenantA = tenantRepository.saveAndFlush(buildTenant("tenant-a"));
        tenantB = tenantRepository.saveAndFlush(buildTenant("tenant-b"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Tenant buildTenant(String prefix) {
        return Tenant.builder()
                .name("Agency " + prefix)
                .slug(prefix + "-" + UUID.randomUUID().toString().substring(0, 8))
                .contactEmail("admin@" + prefix + ".test")
                .timezone("UTC")
                .defaultCurrency("GBP")
                .status(TenantStatus.ACTIVE)
                .build();
    }

    private Property saveProperty(UUID tenantId, String name, String slug,
                                  PropertyType type, PropertyStatus status) {
        TenantContext.setTenantId(tenantId);
        Property p = Property.builder()
                .name(name)
                .slug(slug)
                .propertyType(type)
                .status(status)
                .build();
        return propertyRepository.saveAndFlush(p);
    }

    private void enableTenantFilter(UUID tenantId) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    }

    @Test
    @DisplayName("findByIdAndTenantId returns property when tenant matches")
    void findByIdAndTenantId_MatchingTenant_ReturnsProperty() {
        Property saved = saveProperty(tenantA.getId(), "Beach Villa",
                "beach-villa-" + UUID.randomUUID(), PropertyType.VILLA, PropertyStatus.ACTIVE);

        Optional<Property> found = propertyRepository.findByIdAndTenantId(saved.getId(), tenantA.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Beach Villa");
    }

    @Test
    @DisplayName("findByIdAndTenantId returns empty when tenant does not match (tenant isolation)")
    void findByIdAndTenantId_WrongTenant_ReturnsEmpty() {
        Property saved = saveProperty(tenantA.getId(), "Beach Villa",
                "beach-villa-" + UUID.randomUUID(), PropertyType.VILLA, PropertyStatus.ACTIVE);

        Optional<Property> found = propertyRepository.findByIdAndTenantId(saved.getId(), tenantB.getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByTenantIdAndSlug enforces uniqueness scope per tenant")
    void existsByTenantIdAndSlug_True_ForSavedSlug() {
        String slug = "unique-slug-" + UUID.randomUUID();
        saveProperty(tenantA.getId(), "X", slug, PropertyType.APARTMENT, PropertyStatus.ACTIVE);

        assertThat(propertyRepository.existsByTenantIdAndSlug(tenantA.getId(), slug)).isTrue();
        // Same slug does NOT count for another tenant.
        assertThat(propertyRepository.existsByTenantIdAndSlug(tenantB.getId(), slug)).isFalse();
    }

    @Test
    @DisplayName("countByTenantId counts only the given tenant's properties")
    void countByTenantId_ScopedPerTenant() {
        saveProperty(tenantA.getId(), "A1", "a1-" + UUID.randomUUID(), PropertyType.HOUSE, PropertyStatus.ACTIVE);
        saveProperty(tenantA.getId(), "A2", "a2-" + UUID.randomUUID(), PropertyType.HOUSE, PropertyStatus.ACTIVE);
        saveProperty(tenantB.getId(), "B1", "b1-" + UUID.randomUUID(), PropertyType.HOUSE, PropertyStatus.ACTIVE);

        assertThat(propertyRepository.countByTenantId(tenantA.getId())).isEqualTo(2);
        assertThat(propertyRepository.countByTenantId(tenantB.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("findAll under Hibernate tenant filter only returns the tenant's properties")
    void findAll_WithTenantFilter_IsolatesTenants() {
        saveProperty(tenantA.getId(), "A1", "fa-" + UUID.randomUUID(), PropertyType.HOUSE, PropertyStatus.ACTIVE);
        saveProperty(tenantB.getId(), "B1", "fb-" + UUID.randomUUID(), PropertyType.HOUSE, PropertyStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();

        enableTenantFilter(tenantB.getId());

        List<Property> visible = propertyRepository.findAll();

        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).getName()).isEqualTo("B1");
    }

    @Test
    @DisplayName("search filters by status + propertyType + name substring, paginated")
    void search_FiltersAndPaginates() {
        saveProperty(tenantA.getId(), "Sunny Villa", "s-villa-" + UUID.randomUUID(),
                PropertyType.VILLA, PropertyStatus.ACTIVE);
        saveProperty(tenantA.getId(), "Sunny Apartment", "s-apt-" + UUID.randomUUID(),
                PropertyType.APARTMENT, PropertyStatus.ACTIVE);
        saveProperty(tenantA.getId(), "Archived Villa", "a-villa-" + UUID.randomUUID(),
                PropertyType.VILLA, PropertyStatus.ARCHIVED);
        saveProperty(tenantB.getId(), "Other Tenant Villa", "o-villa-" + UUID.randomUUID(),
                PropertyType.VILLA, PropertyStatus.ACTIVE);

        // Active villas for tenant A — should NOT include archived, apartment, or tenant B.
        Page<Property> page = propertyRepository.search(
                tenantA.getId(), PropertyStatus.ACTIVE, PropertyType.VILLA, null,
                PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(Property::getName)
                .containsExactly("Sunny Villa");

        // Name substring search.
        Page<Property> sunny = propertyRepository.search(
                tenantA.getId(), null, null, "sunny",
                PageRequest.of(0, 10));

        assertThat(sunny.getContent())
                .extracting(Property::getName)
                .containsExactlyInAnyOrder("Sunny Villa", "Sunny Apartment");
    }

    @Test
    @DisplayName("saved property carries audit fields from BaseEntity")
    void save_PersistsAuditFields() {
        Property saved = saveProperty(tenantA.getId(), "Audited", "audited-" + UUID.randomUUID(),
                PropertyType.STUDIO, PropertyStatus.ACTIVE);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(tenantA.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
    }
}
