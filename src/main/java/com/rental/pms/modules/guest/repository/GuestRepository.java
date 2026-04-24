package com.rental.pms.modules.guest.repository;

import com.rental.pms.modules.guest.entity.Guest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

    Optional<Guest> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Guest> findByTenantIdAndEmailHash(UUID tenantId, String emailHash);

    boolean existsByTenantIdAndEmailHash(UUID tenantId, String emailHash);

    /**
     * Tenant-scoped search. {@code search} matches first/last name (substring, case-insensitive).
     * Email is encrypted and therefore not searchable from the DB side; email lookups must use
     * {@link #findByTenantIdAndEmailHash(UUID, String)} via the hash.
     * <p>
     * Note: CAST(:search AS string) is required so Postgres doesn't infer the parameter as bytea
     * when null (which would break LOWER(...)).
     */
    @Query("""
            SELECT g FROM Guest g
            WHERE g.tenantId = :tenantId
              AND (CAST(:search AS string) IS NULL
                   OR LOWER(g.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(g.lastName)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """)
    Page<Guest> search(@Param("tenantId") UUID tenantId,
                       @Param("search") String search,
                       Pageable pageable);
}
