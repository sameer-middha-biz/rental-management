package com.rental.pms.modules.guest.repository;

import com.rental.pms.common.security.TenantContext;
import com.rental.pms.modules.guest.entity.Guest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("GuestRepository Integration Tests")
class GuestRepositoryIntegrationTest {

    @Autowired private GuestRepository guestRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

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

    private Guest saveGuest(UUID tenantId, String firstName, String lastName,
                            String email, String phone, String notes) {
        TenantContext.setTenantId(tenantId);
        Guest g = Guest.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(phone)
                .notes(notes)
                .emailHash(email == null ? null : "hash-" + UUID.randomUUID())
                .build();
        return guestRepository.saveAndFlush(g);
    }

    private void enableTenantFilter(UUID tenantId) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    }

    @Test
    @DisplayName("encrypted fields round-trip: ciphertext stored, plaintext read back")
    void encryptedRoundTrip_WorksViaConverter() {
        Guest saved = saveGuest(tenantA.getId(), "Jane", "Doe",
                "jane@example.com", "+44123456", "VIP guest");

        entityManager.flush();
        entityManager.clear();

        // Raw SQL read: column should NOT contain the plaintext (i.e. it's encrypted).
        String rawEmail = jdbcTemplate.queryForObject(
                "SELECT email FROM guests WHERE id = ?", String.class, saved.getId());
        String rawPhone = jdbcTemplate.queryForObject(
                "SELECT phone FROM guests WHERE id = ?", String.class, saved.getId());
        String rawNotes = jdbcTemplate.queryForObject(
                "SELECT notes FROM guests WHERE id = ?", String.class, saved.getId());

        assertThat(rawEmail).isNotBlank().isNotEqualTo("jane@example.com");
        assertThat(rawPhone).isNotBlank().isNotEqualTo("+44123456");
        assertThat(rawNotes).isNotBlank().isNotEqualTo("VIP guest");

        // JPA read via converter: decrypts back to plaintext.
        Guest reloaded = guestRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("jane@example.com");
        assertThat(reloaded.getPhone()).isEqualTo("+44123456");
        assertThat(reloaded.getNotes()).isEqualTo("VIP guest");
    }

    @Test
    @DisplayName("findByIdAndTenantId isolates tenants")
    void findByIdAndTenantId_OnlyMatchesOwnTenant() {
        Guest saved = saveGuest(tenantA.getId(), "Jane", "Doe", "jane@a.com", null, null);

        assertThat(guestRepository.findByIdAndTenantId(saved.getId(), tenantA.getId())).isPresent();
        assertThat(guestRepository.findByIdAndTenantId(saved.getId(), tenantB.getId())).isEmpty();
    }

    @Test
    @DisplayName("findAll under tenant filter isolates tenants")
    void findAll_WithTenantFilter_IsolatesTenants() {
        saveGuest(tenantA.getId(), "JaneA", "Doe", "a@x.com", null, null);
        saveGuest(tenantB.getId(), "JaneB", "Doe", "b@x.com", null, null);

        entityManager.flush();
        entityManager.clear();
        enableTenantFilter(tenantB.getId());

        var visible = guestRepository.findAll();
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).getFirstName()).isEqualTo("JaneB");
    }

    @Test
    @DisplayName("search finds by name substring, case-insensitive, scoped to tenant")
    void search_FiltersCorrectly() {
        saveGuest(tenantA.getId(), "Alice", "Archer", "alice@x.com", null, null);
        saveGuest(tenantA.getId(), "Bob", "Archer", "bob@x.com", null, null);
        saveGuest(tenantA.getId(), "Cara", "Zephyr", "cara@x.com", null, null);
        saveGuest(tenantB.getId(), "Other", "Archer", "other@x.com", null, null);

        var page = guestRepository.search(tenantA.getId(), "archer",
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(Guest::getFirstName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    @DisplayName("existsByTenantIdAndEmailHash is scoped per tenant")
    void existsByTenantIdAndEmailHash_ScopedPerTenant() {
        TenantContext.setTenantId(tenantA.getId());
        String hash = "canonical-hash-" + UUID.randomUUID();
        Guest g = Guest.builder()
                .firstName("Jane").lastName("Doe")
                .email("j@x.com").emailHash(hash).build();
        guestRepository.saveAndFlush(g);

        assertThat(guestRepository.existsByTenantIdAndEmailHash(tenantA.getId(), hash)).isTrue();
        assertThat(guestRepository.existsByTenantIdAndEmailHash(tenantB.getId(), hash)).isFalse();
    }

    @Test
    @DisplayName("findByTenantIdAndEmailHash returns the saved guest")
    void findByTenantIdAndEmailHash_ReturnsSaved() {
        TenantContext.setTenantId(tenantA.getId());
        String hash = "hash-" + UUID.randomUUID();
        Guest g = Guest.builder()
                .firstName("Jane").lastName("Doe")
                .email("j@x.com").emailHash(hash).build();
        Guest saved = guestRepository.saveAndFlush(g);

        Optional<Guest> found = guestRepository.findByTenantIdAndEmailHash(tenantA.getId(), hash);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("saved guest carries BaseEntity audit fields")
    void save_PersistsAuditFields() {
        Guest saved = saveGuest(tenantA.getId(), "A", "B", null, null, null);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(tenantA.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
