package com.rental.pms.modules.property.repository;

import com.rental.pms.modules.property.entity.Property;
import com.rental.pms.modules.property.entity.PropertyStatus;
import com.rental.pms.modules.property.entity.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    Optional<Property> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    long countByTenantId(UUID tenantId);

    /**
     * Tenant-scoped, filter-capable listing. All filters are optional (pass null to skip).
     */
    // Note: CAST(:search AS string) is required so Postgres doesn't infer
    // the parameter type as bytea when the argument is null (which would
    // break LOWER(...) with "function lower(bytea) does not exist").
    @Query("""
            SELECT p FROM Property p
            WHERE p.tenantId = :tenantId
              AND (:status IS NULL OR p.status = :status)
              AND (:propertyType IS NULL OR p.propertyType = :propertyType)
              AND (CAST(:search AS string) IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """)
    Page<Property> search(
            @Param("tenantId") UUID tenantId,
            @Param("status") PropertyStatus status,
            @Param("propertyType") PropertyType propertyType,
            @Param("search") String search,
            Pageable pageable);
}
