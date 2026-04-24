package com.rental.pms.modules.guest.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.common.event.DomainEvent;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuestService {

    private final GuestRepository guestRepository;
    private final GuestMapper guestMapper;
    private final DomainEventPublisher domainEventPublisher;
    private final AuditEventPublisher auditEventPublisher;
    private final CurrentUser currentUser;
    private final S3Client s3Client;

    @Value("${pms.s3.bucket-name}")
    private String bucketName;

    @Transactional
    public GuestResponse create(CreateGuestRequest request) {
        UUID tenantId = currentUser.getTenantId();

        String emailHash = hashEmail(request.email(), tenantId);
        if (emailHash != null && guestRepository.existsByTenantIdAndEmailHash(tenantId, emailHash)) {
            throw new ConflictException(
                    "A guest with this email already exists for the tenant",
                    "GUEST.EMAIL.DUPLICATE");
        }

        Guest guest = guestMapper.toEntity(request);
        guest.setEmailHash(emailHash);
        // tenantId populated by TenantInterceptor from TenantContext
        guest = guestRepository.save(guest);

        domainEventPublisher.publish(new GuestCreatedEvent(
                DomainEvent.now(tenantId), tenantId, guest.getId()));

        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "GUEST_CREATED", "Guest", guest.getId(),
                "Guest created: " + guest.getFirstName() + " " + guest.getLastName());

        log.info("Guest created: id={}, tenantId={}", guest.getId(), tenantId);
        return guestMapper.toResponse(guest);
    }

    @Transactional
    public GuestResponse update(UUID id, UpdateGuestRequest request) {
        UUID tenantId = currentUser.getTenantId();
        Guest guest = findOrThrow(id, tenantId);

        if (guest.isGdprErased()) {
            throw new ConflictException(
                    "Cannot modify a GDPR-erased guest record",
                    "GUEST.GDPR_ERASED");
        }

        // Email change — recompute hash and re-check uniqueness (only if actually changing).
        if (request.email() != null && !Objects.equals(request.email(), guest.getEmail())) {
            String newHash = hashEmail(request.email(), tenantId);
            if (newHash != null
                    && !Objects.equals(newHash, guest.getEmailHash())
                    && guestRepository.existsByTenantIdAndEmailHash(tenantId, newHash)) {
                throw new ConflictException(
                        "A guest with this email already exists for the tenant",
                        "GUEST.EMAIL.DUPLICATE");
            }
            guest.setEmailHash(newHash);
        }

        guestMapper.applyUpdate(request, guest);
        // update() is intentionally a no-op for persistence — dirty checking handles it.

        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "GUEST_UPDATED", "Guest", guest.getId(),
                "Guest updated: " + guest.getId());

        return guestMapper.toResponse(guest);
    }

    @Transactional(readOnly = true)
    public GuestResponse getById(UUID id) {
        UUID tenantId = currentUser.getTenantId();
        return guestMapper.toResponse(findOrThrow(id, tenantId));
    }

    @Transactional(readOnly = true)
    public PageResponse<GuestResponse> search(String search, Pageable pageable) {
        UUID tenantId = currentUser.getTenantId();
        Page<Guest> page = guestRepository.search(tenantId, search, pageable);
        return PageResponse.from(page.map(guestMapper::toResponse));
    }

    /**
     * GDPR erasure (Right to be Forgotten).
     * <p>
     * Anonymises PII in place rather than deleting the row, so bookings referencing
     * this guest remain valid for audit/financial records. Deletes the ID document
     * from S3 if present. Publishes an audit event for compliance evidence.
     */
    @Transactional
    public void gdprErase(UUID id) {
        UUID tenantId = currentUser.getTenantId();
        Guest guest = findOrThrow(id, tenantId);

        if (guest.isGdprErased()) {
            // Idempotent — already erased is fine, just log and return.
            log.debug("Guest already GDPR-erased: id={}", id);
            return;
        }

        // Delete ID document from S3 (best-effort: log and proceed if S3 unreachable;
        // orphaned objects can be reconciled by ops without breaking the erasure).
        String docKey = guest.getIdDocumentS3Key();
        if (docKey != null) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(docKey)
                        .build());
            } catch (NoSuchKeyException nsk) {
                log.debug("GDPR erase: S3 key already absent: {}", docKey);
            } catch (Exception e) {
                log.warn("GDPR erase: failed to delete S3 object {}: {}", docKey, e.getMessage());
            }
        }

        guest.setFirstName("DELETED");
        guest.setLastName("DELETED");
        guest.setEmail("deleted-" + UUID.randomUUID() + "@erased.local");
        guest.setEmailHash(null);
        guest.setPhone(null);
        guest.setNationality(null);
        guest.setNotes(null);
        guest.setIdDocumentS3Key(null);
        guest.setGdprErased(true);
        guest.setGdprErasedAt(Instant.now());

        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "GDPR_ERASURE", "Guest", guest.getId(),
                "GDPR erasure completed for guest " + guest.getId());

        log.info("GDPR erasure completed: guestId={}, tenantId={}", guest.getId(), tenantId);
    }

    private Guest findOrThrow(UUID id, UUID tenantId) {
        return guestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest", "id", id));
    }

    /**
     * Computes SHA-256(lower(email) + ":" + tenantId) as a stable lookup key.
     * Returns null if email is null/blank. The tenantId suffix prevents cross-tenant
     * correlation if the hash ever leaks.
     */
    static String hashEmail(String email, UUID tenantId) {
        if (email == null || email.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = email.trim().toLowerCase() + ":" + tenantId;
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JDK; this is unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
