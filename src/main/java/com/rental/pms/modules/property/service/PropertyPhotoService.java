package com.rental.pms.modules.property.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.property.dto.ConfirmPhotoUploadRequest;
import com.rental.pms.modules.property.dto.GenerateUploadUrlRequest;
import com.rental.pms.modules.property.dto.GenerateUploadUrlResponse;
import com.rental.pms.modules.property.dto.PropertyPhotoResponse;
import com.rental.pms.modules.property.dto.ReorderPhotosRequest;
import com.rental.pms.modules.property.entity.Property;
import com.rental.pms.modules.property.entity.PropertyPhoto;
import com.rental.pms.modules.property.mapper.PropertyPhotoMapper;
import com.rental.pms.modules.property.repository.PropertyPhotoRepository;
import com.rental.pms.modules.property.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyPhotoService {

    private final PropertyRepository propertyRepository;
    private final PropertyPhotoRepository photoRepository;
    private final PropertyPhotoMapper photoMapper;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final AuditEventPublisher auditEventPublisher;
    private final CurrentUser currentUser;

    @Value("${pms.s3.bucket-name}")
    private String bucketName;

    @Value("${pms.s3.presigned-url-expiry-minutes:15}")
    private int presignedUrlExpiryMinutes;

    @Value("${pms.s3.max-upload-size-bytes:10485760}")
    private long maxUploadSizeBytes;

    /**
     * Generates a pre-signed S3 PUT URL for the client to upload a photo directly.
     * The client must call {@link #confirmUpload} afterwards to create the DB record.
     * Enforces per-photo size limit via {@code pms.s3.max-upload-size-bytes}.
     */
    public GenerateUploadUrlResponse generateUploadUrl(UUID propertyId, GenerateUploadUrlRequest request) {
        UUID tenantId = currentUser.getTenantId();
        Property property = findPropertyOrThrow(propertyId, tenantId);

        if (request.sizeBytes() != null && request.sizeBytes() > maxUploadSizeBytes) {
            throw new ConflictException(
                    "File exceeds maximum upload size of " + maxUploadSizeBytes + " bytes",
                    "PROPERTY.PHOTO.TOO_LARGE");
        }

        String s3Key = buildS3Key(tenantId, property.getId(), request.filename());
        Duration expiry = Duration.ofMinutes(presignedUrlExpiryMinutes);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(request.contentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        Instant expiresAt = Instant.now().plus(expiry);

        log.debug("Generated pre-signed URL: tenantId={}, propertyId={}, key={}, expiresAt={}",
                tenantId, propertyId, s3Key, expiresAt);

        return new GenerateUploadUrlResponse(
                presigned.url().toString(),
                s3Key,
                "PUT",
                expiresAt);
    }

    /**
     * Records a successful upload. Called by the client after PUTting to the pre-signed URL.
     * Verifies the uploaded s3Key matches the expected prefix for this tenant/property
     * (prevents cross-tenant injection even if the client lies about the key).
     */
    @Transactional
    public PropertyPhotoResponse confirmUpload(UUID propertyId, ConfirmPhotoUploadRequest request) {
        UUID tenantId = currentUser.getTenantId();
        Property property = findPropertyOrThrow(propertyId, tenantId);

        validateS3KeyOwnership(request.s3Key(), tenantId, property.getId());

        int nextSortOrder = (int) photoRepository.countByPropertyId(property.getId());

        PropertyPhoto photo = new PropertyPhoto();
        photo.setPropertyId(property.getId());
        photo.setS3Key(request.s3Key());
        photo.setFilename(request.filename());
        photo.setContentType(request.contentType());
        photo.setSizeBytes(request.sizeBytes());
        photo.setSortOrder(nextSortOrder);
        photo.setCaption(request.caption());
        photo = photoRepository.save(photo);

        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "PROPERTY_PHOTO_ADDED", "PropertyPhoto", photo.getId(),
                "Photo added to property " + property.getId());

        log.info("Photo confirmed: id={}, propertyId={}, s3Key={}",
                photo.getId(), property.getId(), request.s3Key());

        return photoMapper.toResponse(photo);
    }

    public List<PropertyPhotoResponse> listPhotos(UUID propertyId) {
        UUID tenantId = currentUser.getTenantId();
        findPropertyOrThrow(propertyId, tenantId);
        return photoRepository.findByPropertyIdOrderBySortOrderAsc(propertyId).stream()
                .map(photoMapper::toResponse)
                .toList();
    }

    /**
     * Sets {@code sort_order} for each photo based on the position in {@code request.photoIds()}.
     * All IDs must belong to the given property/tenant; partial lists are rejected.
     */
    @Transactional
    public List<PropertyPhotoResponse> reorderPhotos(UUID propertyId, ReorderPhotosRequest request) {
        UUID tenantId = currentUser.getTenantId();
        findPropertyOrThrow(propertyId, tenantId);

        List<PropertyPhoto> existing = photoRepository.findByPropertyIdOrderBySortOrderAsc(propertyId);

        if (existing.size() != request.photoIds().size()) {
            throw new ConflictException(
                    "Reorder list must contain exactly all photo IDs for this property (expected "
                            + existing.size() + ", got " + request.photoIds().size() + ")",
                    "PROPERTY.PHOTO.REORDER_MISMATCH");
        }

        Map<UUID, PropertyPhoto> byId = new HashMap<>();
        existing.forEach(p -> byId.put(p.getId(), p));

        for (int i = 0; i < request.photoIds().size(); i++) {
            UUID photoId = request.photoIds().get(i);
            PropertyPhoto photo = byId.get(photoId);
            if (photo == null) {
                throw new ConflictException(
                        "Photo " + photoId + " does not belong to property " + propertyId,
                        "PROPERTY.PHOTO.REORDER_UNKNOWN_ID");
            }
            photo.setSortOrder(i);
        }
        photoRepository.saveAll(byId.values());

        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "PROPERTY_PHOTOS_REORDERED", "Property", propertyId,
                "Photos reordered for property " + propertyId);

        return existing.stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(photoMapper::toResponse)
                .toList();
    }

    @Transactional
    public void deletePhoto(UUID propertyId, UUID photoId) {
        UUID tenantId = currentUser.getTenantId();
        PropertyPhoto photo = photoRepository.findByIdAndPropertyIdAndTenantId(photoId, propertyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("PropertyPhoto", "id", photoId));

        // Best-effort S3 cleanup — don't fail the delete if S3 is unreachable,
        // but log loudly so ops can reconcile orphans later.
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(photo.getS3Key())
                    .build());
        } catch (NoSuchKeyException nsk) {
            log.debug("S3 key already absent: {}", photo.getS3Key());
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {}: {}", photo.getS3Key(), e.getMessage());
        }

        photoRepository.delete(photo);

        auditEventPublisher.publish(tenantId, currentUser.getUserId(),
                "PROPERTY_PHOTO_DELETED", "PropertyPhoto", photoId,
                "Photo deleted from property " + propertyId);
    }

    private Property findPropertyOrThrow(UUID propertyId, UUID tenantId) {
        return propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));
    }

    /**
     * Generates a tenant/property-scoped S3 key. Prefixing with tenant+property IDs
     * means a stolen/leaked pre-signed URL can never target a different tenant's object.
     */
    static String buildS3Key(UUID tenantId, UUID propertyId, String filename) {
        String safeFilename = sanitizeFilename(filename);
        return String.format("tenants/%s/properties/%s/photos/%s-%s",
                tenantId, propertyId, UUID.randomUUID(), safeFilename);
    }

    static String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        String name = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.length() > 100) name = name.substring(name.length() - 100);
        return name.isEmpty() ? "file" : name;
    }

    private void validateS3KeyOwnership(String s3Key, UUID tenantId, UUID propertyId) {
        String expectedPrefix = String.format("tenants/%s/properties/%s/photos/", tenantId, propertyId);
        if (s3Key == null || !s3Key.startsWith(expectedPrefix)) {
            throw new ConflictException(
                    "s3Key does not belong to this property",
                    "PROPERTY.PHOTO.INVALID_KEY");
        }
    }
}
