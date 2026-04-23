package com.rental.pms.modules.property.repository;

import com.rental.pms.modules.property.entity.PropertyPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyPhotoRepository extends JpaRepository<PropertyPhoto, UUID> {

    List<PropertyPhoto> findByPropertyIdOrderBySortOrderAsc(UUID propertyId);

    Optional<PropertyPhoto> findByIdAndPropertyIdAndTenantId(UUID id, UUID propertyId, UUID tenantId);

    long countByPropertyId(UUID propertyId);

    void deleteByPropertyId(UUID propertyId);
}
