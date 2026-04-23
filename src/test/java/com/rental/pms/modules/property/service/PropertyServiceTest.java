package com.rental.pms.modules.property.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.event.DomainEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.exception.TenantLimitExceededException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.property.dto.CreatePropertyRequest;
import com.rental.pms.modules.property.dto.PropertyResponse;
import com.rental.pms.modules.property.dto.UpdatePropertyRequest;
import com.rental.pms.modules.property.entity.Property;
import com.rental.pms.modules.property.entity.PropertyStatus;
import com.rental.pms.modules.property.entity.PropertyType;
import com.rental.pms.modules.property.event.PropertyArchivedEvent;
import com.rental.pms.modules.property.event.PropertyCreatedEvent;
import com.rental.pms.modules.property.mapper.PropertyMapper;
import com.rental.pms.modules.property.repository.PropertyRepository;
import com.rental.pms.modules.subscription.service.PlanEnforcementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private PropertyMapper propertyMapper;

    @Mock
    private PlanEnforcementService planEnforcementService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private PropertyService propertyService;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private CreatePropertyRequest validCreateRequest() {
        return new CreatePropertyRequest(
                "Seaside Villa", null, "Cozy seaside villa",
                "VILLA", null,
                "1 Beach Rd", null, "Brighton", null, "BN1 1AA", "GBR",
                null, null,
                6, 3, 2, 4,
                null, null, null);
    }

    private Property entityWithId() {
        Property p = new Property();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setName("Seaside Villa");
        p.setSlug("seaside-villa");
        p.setPropertyType(PropertyType.VILLA);
        p.setStatus(PropertyStatus.ACTIVE);
        return p;
    }

    @Test
    void create_UnderLimit_SavesAndPublishesEvents() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(currentUser.getUserId()).thenReturn(userId);
        when(propertyRepository.existsByTenantIdAndSlug(eq(tenantId), any())).thenReturn(false);

        Property mapped = new Property();
        mapped.setName("Seaside Villa");
        mapped.setPropertyType(PropertyType.VILLA);
        when(propertyMapper.toEntity(any(CreatePropertyRequest.class))).thenReturn(mapped);

        Property saved = entityWithId();
        when(propertyRepository.save(any(Property.class))).thenReturn(saved);
        PropertyResponse expectedResponse = new PropertyResponse(
                saved.getId(), tenantId, null, "Seaside Villa", "seaside-villa",
                null, "VILLA", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                "ACTIVE", null, Instant.now(), Instant.now());
        when(propertyMapper.toResponse(saved)).thenReturn(expectedResponse);

        PropertyResponse result = propertyService.create(validCreateRequest());

        assertThat(result.name()).isEqualTo("Seaside Villa");
        assertThat(result.slug()).isEqualTo("seaside-villa");

        verify(planEnforcementService).checkPropertyLimit(eq(tenantId), any(LongSupplier.class));
        verify(propertyRepository).save(any(Property.class));
        verify(domainEventPublisher).publish(any(PropertyCreatedEvent.class));
        verify(auditEventPublisher).publish(eq(tenantId), eq(userId),
                eq("PROPERTY_CREATED"), eq("Property"), any(UUID.class), any());
    }

    @Test
    void create_AtPlanLimit_ThrowsAndDoesNotSave() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        doThrow(new TenantLimitExceededException("properties", 5, 5))
                .when(planEnforcementService).checkPropertyLimit(eq(tenantId), any(LongSupplier.class));

        assertThatThrownBy(() -> propertyService.create(validCreateRequest()))
                .isInstanceOf(TenantLimitExceededException.class);

        verify(propertyRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void create_SlugCollision_AppendsSuffix() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(currentUser.getUserId()).thenReturn(userId);
        // "seaside-villa" taken, "seaside-villa-2" available
        when(propertyRepository.existsByTenantIdAndSlug(tenantId, "seaside-villa")).thenReturn(true);
        when(propertyRepository.existsByTenantIdAndSlug(tenantId, "seaside-villa-2")).thenReturn(false);

        Property mapped = new Property();
        mapped.setName("Seaside Villa");
        mapped.setPropertyType(PropertyType.VILLA);
        when(propertyMapper.toEntity(any(CreatePropertyRequest.class))).thenReturn(mapped);
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));
        when(propertyMapper.toResponse(any(Property.class))).thenAnswer(inv -> {
            Property p = inv.getArgument(0);
            return new PropertyResponse(p.getId(), p.getTenantId(), null,
                    p.getName(), p.getSlug(), null, "VILLA", null, null, null,
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, "ACTIVE", null, null, null);
        });

        PropertyResponse result = propertyService.create(validCreateRequest());

        assertThat(result.slug()).isEqualTo("seaside-villa-2");
    }

    @Test
    void getById_NotFound_ThrowsResourceNotFound() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        UUID id = UUID.randomUUID();
        when(propertyRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> propertyService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_AppliesPartialChanges() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(currentUser.getUserId()).thenReturn(userId);
        Property existing = entityWithId();
        when(propertyRepository.findByIdAndTenantId(existing.getId(), tenantId))
                .thenReturn(Optional.of(existing));
        when(propertyRepository.save(existing)).thenReturn(existing);
        when(propertyMapper.toResponse(existing)).thenReturn(
                new PropertyResponse(existing.getId(), tenantId, null,
                        "Seaside Villa", "seaside-villa", null, "VILLA",
                        null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null,
                        "ACTIVE", null, null, null));

        UpdatePropertyRequest req = new UpdatePropertyRequest(
                "Renamed", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        PropertyResponse result = propertyService.update(existing.getId(), req);

        assertThat(result).isNotNull();
        verify(propertyMapper).applyUpdate(req, existing);
    }

    @Test
    void archive_ActiveProperty_FlipsStatusAndPublishesEvent() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(currentUser.getUserId()).thenReturn(userId);
        Property existing = entityWithId();
        when(propertyRepository.findByIdAndTenantId(existing.getId(), tenantId))
                .thenReturn(Optional.of(existing));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));
        when(propertyMapper.toResponse(any(Property.class))).thenAnswer(inv -> {
            Property p = inv.getArgument(0);
            return new PropertyResponse(p.getId(), p.getTenantId(), null, p.getName(),
                    p.getSlug(), null, "VILLA", null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null,
                    p.getStatus().name(), p.getArchivedAt(), null, null);
        });

        PropertyResponse result = propertyService.archive(existing.getId());

        assertThat(result.status()).isEqualTo("ARCHIVED");
        assertThat(existing.getArchivedAt()).isNotNull();
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PropertyArchivedEvent.class);
    }

    @Test
    void archive_AlreadyArchived_ThrowsConflict() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        Property existing = entityWithId();
        existing.setStatus(PropertyStatus.ARCHIVED);
        when(propertyRepository.findByIdAndTenantId(existing.getId(), tenantId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> propertyService.archive(existing.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void restore_NotArchived_ThrowsConflict() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        Property existing = entityWithId();
        // status is ACTIVE
        when(propertyRepository.findByIdAndTenantId(existing.getId(), tenantId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> propertyService.restore(existing.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void delete_ExistingProperty_DeletesAndAudits() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(currentUser.getUserId()).thenReturn(userId);
        Property existing = entityWithId();
        when(propertyRepository.findByIdAndTenantId(existing.getId(), tenantId))
                .thenReturn(Optional.of(existing));

        propertyService.delete(existing.getId());

        verify(propertyRepository).delete(existing);
        verify(auditEventPublisher).publish(eq(tenantId), eq(userId),
                eq("PROPERTY_DELETED"), eq("Property"), eq(existing.getId()), any());
    }

    @Test
    void slugify_HandlesSpecialChars() {
        assertThat(PropertyService.slugify("Villa París & Sons!"))
                .isEqualTo("villa-paris-sons");
        assertThat(PropertyService.slugify("  Multiple   spaces  "))
                .isEqualTo("multiple-spaces");
    }
}
