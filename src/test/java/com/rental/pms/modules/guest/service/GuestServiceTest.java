package com.rental.pms.modules.guest.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.event.DomainEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.guest.dto.CreateGuestRequest;
import com.rental.pms.modules.guest.dto.GuestResponse;
import com.rental.pms.modules.guest.dto.UpdateGuestRequest;
import com.rental.pms.modules.guest.entity.Guest;
import com.rental.pms.modules.guest.event.GuestCreatedEvent;
import com.rental.pms.modules.guest.mapper.GuestMapper;
import com.rental.pms.modules.guest.repository.GuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestServiceTest {

    @Mock private GuestRepository guestRepository;
    @Mock private GuestMapper guestMapper;
    @Mock private DomainEventPublisher domainEventPublisher;
    @Mock private AuditEventPublisher auditEventPublisher;
    @Mock private CurrentUser currentUser;
    @Mock private S3Client s3Client;

    @InjectMocks
    private GuestService guestService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(guestService, "bucketName", "test-bucket");
        when(currentUser.getTenantId()).thenReturn(tenantId);
        // Lenient on user id; not all tests exercise audit.
        when(currentUser.getUserId()).thenReturn(actorUserId);
    }

    private CreateGuestRequest createRequest(String email) {
        return new CreateGuestRequest(
                "Jane", "Doe", email, "+44123", "GBR", null, "likes quiet rooms");
    }

    private Guest savedGuest(UUID id) {
        Guest g = Guest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .build();
        g.setId(id);
        g.setTenantId(tenantId);
        return g;
    }

    @Test
    void create_NewEmail_PersistsSetsHashAndPublishesEvent() {
        CreateGuestRequest req = createRequest("Jane@Example.com");
        String expectedHash = GuestService.hashEmail(req.email(), tenantId);

        Guest mapped = Guest.builder().firstName("Jane").lastName("Doe").email(req.email()).build();
        when(guestMapper.toEntity(req)).thenReturn(mapped);
        when(guestRepository.existsByTenantIdAndEmailHash(tenantId, expectedHash)).thenReturn(false);

        UUID generatedId = UUID.randomUUID();
        when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> {
            Guest g = inv.getArgument(0);
            g.setId(generatedId);
            g.setTenantId(tenantId);
            return g;
        });
        when(guestMapper.toResponse(any(Guest.class))).thenReturn(
                new GuestResponse(generatedId, tenantId, "Jane", "Doe", req.email(),
                        req.phone(), "GBR", null, req.notes(), 0, null, false, null, null, null));

        GuestResponse resp = guestService.create(req);

        assertThat(resp.id()).isEqualTo(generatedId);

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestRepository).save(captor.capture());
        assertThat(captor.getValue().getEmailHash()).isEqualTo(expectedHash);

        verify(domainEventPublisher).publish(any(GuestCreatedEvent.class));
        verify(auditEventPublisher).publish(eq(tenantId), eq(actorUserId),
                eq("GUEST_CREATED"), eq("Guest"), eq(generatedId), any());
    }

    @Test
    void create_DuplicateEmail_ThrowsConflict() {
        CreateGuestRequest req = createRequest("dup@example.com");
        String hash = GuestService.hashEmail(req.email(), tenantId);
        when(guestRepository.existsByTenantIdAndEmailHash(tenantId, hash)).thenReturn(true);

        assertThatThrownBy(() -> guestService.create(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
        verify(guestRepository, never()).save(any());
    }

    @Test
    void create_NullEmail_StillSucceedsWithNullHash() {
        CreateGuestRequest req = new CreateGuestRequest("Bob", "Brown",
                null, null, null, null, null);
        Guest mapped = Guest.builder().firstName("Bob").lastName("Brown").build();
        when(guestMapper.toEntity(req)).thenReturn(mapped);

        UUID id = UUID.randomUUID();
        when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> {
            Guest g = inv.getArgument(0);
            g.setId(id);
            g.setTenantId(tenantId);
            return g;
        });
        when(guestMapper.toResponse(any(Guest.class))).thenReturn(
                new GuestResponse(id, tenantId, "Bob", "Brown", null, null, null, null, null,
                        0, null, false, null, null, null));

        guestService.create(req);

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestRepository).save(captor.capture());
        assertThat(captor.getValue().getEmailHash()).isNull();
        // No uniqueness check should have been issued for a null email.
        verify(guestRepository, never()).existsByTenantIdAndEmailHash(any(), any());
    }

    @Test
    void update_ChangingEmail_RecomputesHashAndChecksUniqueness() {
        UUID id = UUID.randomUUID();
        Guest existing = savedGuest(id);
        existing.setEmail("old@example.com");
        existing.setEmailHash(GuestService.hashEmail("old@example.com", tenantId));

        when(guestRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        UpdateGuestRequest req = new UpdateGuestRequest(null, null, "new@example.com",
                null, null, null, null);
        String newHash = GuestService.hashEmail(req.email(), tenantId);
        when(guestRepository.existsByTenantIdAndEmailHash(tenantId, newHash)).thenReturn(false);
        when(guestMapper.toResponse(existing)).thenReturn(
                new GuestResponse(id, tenantId, "Jane", "Doe", req.email(), null, null, null, null,
                        0, null, false, null, null, null));

        guestService.update(id, req);

        assertThat(existing.getEmailHash()).isEqualTo(newHash);
    }

    @Test
    void update_GdprErasedGuest_ThrowsConflict() {
        UUID id = UUID.randomUUID();
        Guest existing = savedGuest(id);
        existing.setGdprErased(true);
        when(guestRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> guestService.update(id, new UpdateGuestRequest(
                "X", null, null, null, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("GDPR-erased");
    }

    @Test
    void getById_NotFound_Throws() {
        UUID id = UUID.randomUUID();
        when(guestRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> guestService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void gdprErase_AnonymisesAllPiiFieldsAndDeletesS3Doc() {
        UUID id = UUID.randomUUID();
        Guest existing = savedGuest(id);
        existing.setEmail("jane@example.com");
        existing.setEmailHash(GuestService.hashEmail("jane@example.com", tenantId));
        existing.setPhone("+44123");
        existing.setNotes("VIP");
        existing.setNationality("GBR");
        existing.setIdDocumentS3Key("tenants/x/guests/y/passport.pdf");

        when(guestRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        guestService.gdprErase(id);

        assertThat(existing.getFirstName()).isEqualTo("DELETED");
        assertThat(existing.getLastName()).isEqualTo("DELETED");
        assertThat(existing.getEmail()).startsWith("deleted-").endsWith("@erased.local");
        assertThat(existing.getEmailHash()).isNull();
        assertThat(existing.getPhone()).isNull();
        assertThat(existing.getNotes()).isNull();
        assertThat(existing.getNationality()).isNull();
        assertThat(existing.getIdDocumentS3Key()).isNull();
        assertThat(existing.isGdprErased()).isTrue();
        assertThat(existing.getGdprErasedAt()).isNotNull();

        ArgumentCaptor<DeleteObjectRequest> s3Captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(s3Captor.capture());
        assertThat(s3Captor.getValue().key()).isEqualTo("tenants/x/guests/y/passport.pdf");

        verify(auditEventPublisher).publish(eq(tenantId), eq(actorUserId),
                eq("GDPR_ERASURE"), eq("Guest"), eq(id), any());
    }

    @Test
    void gdprErase_S3FailureDoesNotAbortErasure() {
        UUID id = UUID.randomUUID();
        Guest existing = savedGuest(id);
        existing.setIdDocumentS3Key("tenants/x/guests/y/passport.pdf");
        when(guestRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 down"));

        // Should NOT throw — ops will reconcile orphan objects.
        guestService.gdprErase(id);
        assertThat(existing.isGdprErased()).isTrue();
    }

    @Test
    void gdprErase_NoSuchKey_IsIgnoredSilently() {
        UUID id = UUID.randomUUID();
        Guest existing = savedGuest(id);
        existing.setIdDocumentS3Key("tenants/x/guests/y/passport.pdf");
        when(guestRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("gone").build());

        guestService.gdprErase(id);
        assertThat(existing.isGdprErased()).isTrue();
    }

    @Test
    void gdprErase_AlreadyErased_IsIdempotent() {
        UUID id = UUID.randomUUID();
        Guest existing = savedGuest(id);
        existing.setGdprErased(true);
        when(guestRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        guestService.gdprErase(id);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(auditEventPublisher, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void hashEmail_IsDeterministicAndCaseInsensitiveAndTenantScoped() {
        UUID tA = UUID.randomUUID();
        UUID tB = UUID.randomUUID();
        String h1 = GuestService.hashEmail("Alice@Example.COM", tA);
        String h2 = GuestService.hashEmail("  alice@example.com  ", tA);
        String h3 = GuestService.hashEmail("alice@example.com", tB);

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).isNotEqualTo(h3);  // different tenant → different hash
        assertThat(h1).hasSize(64); // 32 bytes hex
        assertThat(GuestService.hashEmail(null, tA)).isNull();
        assertThat(GuestService.hashEmail(" ", tA)).isNull();
    }
}
