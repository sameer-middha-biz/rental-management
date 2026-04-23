package com.rental.pms.modules.property.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.common.event.DomainEvent;
import com.rental.pms.common.event.DomainEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.property.dto.CreatePropertyRequest;
import com.rental.pms.modules.property.dto.PropertyResponse;
import com.rental.pms.modules.property.dto.UpdatePropertyRequest;
import com.rental.pms.modules.property.entity.Property;
import com.rental.pms.modules.property.entity.PropertyStatus;
import com.rental.pms.modules.property.entity.PropertyType;
import com.rental.pms.modules.property.event.PropertyArchivedEvent;
import com.rental.pms.modules.property.event.PropertyCreatedEvent;
import com.rental.pms.modules.property.event.PropertyUpdatedEvent;
import com.rental.pms.modules.property.mapper.PropertyMapper;
import com.rental.pms.modules.property.repository.PropertyRepository;
import com.rental.pms.modules.subscription.service.PlanEnforcementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;
    private final PlanEnforcementService planEnforcementService;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditEventPublisher auditEventPublisher;
    private final CurrentUser currentUser;

    @Transactional
    public PropertyResponse create(CreatePropertyRequest request) {
        UUID tenantId = currentUser.getTenantId();

        // Plan limit check — lazy count supplier only runs for bounded plans
        planEnforcementService.checkPropertyLimit(tenantId,
                () -> propertyRepository.countByTenantId(tenantId));

        String slug = resolveSlug(tenantId, request.slug(), request.name());

        Property property = propertyMapper.toEntity(request);
        property.setSlug(slug);
        property.setStatus(PropertyStatus.ACTIVE);
        // tenantId populated by TenantInterceptor from TenantContext
        property = propertyRepository.save(property);

        domainEventPublisher.publish(new PropertyCreatedEvent(
                DomainEvent.now(tenantId),
                tenantId, property.getId(), property.getName(),
                property.getPropertyType().name()));
        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "PROPERTY_CREATED", "Property", property.getId(),
                "Property created: " + property.getName());

        log.info("Property created: id={}, tenantId={}, name={}",
                property.getId(), tenantId, property.getName());

        return propertyMapper.toResponse(property);
    }

    public PropertyResponse getById(UUID id) {
        UUID tenantId = currentUser.getTenantId();
        Property property = propertyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
        return propertyMapper.toResponse(property);
    }

    public PageResponse<PropertyResponse> search(PropertyStatus status, PropertyType type,
                                                  String search, Pageable pageable) {
        UUID tenantId = currentUser.getTenantId();
        Page<PropertyResponse> page = propertyRepository
                .search(tenantId, status, type, search, pageable)
                .map(propertyMapper::toResponse);
        return PageResponse.from(page);
    }

    @Transactional
    public PropertyResponse update(UUID id, UpdatePropertyRequest request) {
        UUID tenantId = currentUser.getTenantId();
        Property property = propertyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        propertyMapper.applyUpdate(request, property);
        property = propertyRepository.save(property);

        domainEventPublisher.publish(new PropertyUpdatedEvent(
                DomainEvent.now(tenantId), tenantId, property.getId()));
        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "PROPERTY_UPDATED", "Property", property.getId(),
                "Property updated: " + property.getName());

        return propertyMapper.toResponse(property);
    }

    @Transactional
    public PropertyResponse archive(UUID id) {
        UUID tenantId = currentUser.getTenantId();
        Property property = propertyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        if (property.getStatus() == PropertyStatus.ARCHIVED) {
            throw new ConflictException("Property already archived", "PROPERTY.ALREADY_ARCHIVED");
        }

        property.setStatus(PropertyStatus.ARCHIVED);
        property.setArchivedAt(Instant.now());
        property = propertyRepository.save(property);

        domainEventPublisher.publish(new PropertyArchivedEvent(
                DomainEvent.now(tenantId), tenantId, property.getId()));
        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "PROPERTY_ARCHIVED", "Property", property.getId(),
                "Property archived: " + property.getName());

        log.info("Property archived: id={}, tenantId={}", property.getId(), tenantId);
        return propertyMapper.toResponse(property);
    }

    @Transactional
    public PropertyResponse restore(UUID id) {
        UUID tenantId = currentUser.getTenantId();
        Property property = propertyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        if (property.getStatus() == PropertyStatus.ACTIVE) {
            throw new ConflictException("Property is not archived", "PROPERTY.NOT_ARCHIVED");
        }

        // Restoring counts against the plan again; re-check before flipping status
        planEnforcementService.checkPropertyLimit(tenantId,
                () -> propertyRepository.countByTenantId(tenantId)
                        - 1 /* exclude this archived row from the count */);
        // Note: simpler — require capacity for an additional active property
        // The supplier above returns the count of ACTIVE+ARCHIVED; subtract 1 to reflect
        // that this archived row will become active. For strict plan enforcement we
        // count ACTIVE only; see repository.countByTenantIdAndStatus (future enhancement).

        property.setStatus(PropertyStatus.ACTIVE);
        property.setArchivedAt(null);
        property = propertyRepository.save(property);

        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "PROPERTY_RESTORED", "Property", property.getId(),
                "Property restored: " + property.getName());

        return propertyMapper.toResponse(property);
    }

    /**
     * Hard delete. Archiving is preferred; delete is reserved for admin cleanup.
     */
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = currentUser.getTenantId();
        Property property = propertyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));

        propertyRepository.delete(property);

        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "PROPERTY_DELETED", "Property", property.getId(),
                "Property deleted: " + property.getName());

        log.info("Property deleted: id={}, tenantId={}", id, tenantId);
    }

    private String resolveSlug(UUID tenantId, String requestedSlug, String name) {
        String base = (requestedSlug != null && !requestedSlug.isBlank())
                ? slugify(requestedSlug)
                : slugify(name);
        if (base.isEmpty()) {
            base = "property";
        }
        String candidate = base;
        int suffix = 2;
        while (propertyRepository.existsByTenantIdAndSlug(tenantId, candidate)) {
            candidate = base + "-" + suffix++;
            if (suffix > 1000) {
                throw new IllegalStateException("Unable to generate unique property slug after 1000 attempts");
            }
        }
        return candidate;
    }

    static String slugify(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        if (slug.length() > 200) slug = slug.substring(0, 200);
        return slug;
    }
}
