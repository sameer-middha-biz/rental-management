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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyPhotoServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private PropertyPhotoRepository photoRepository;

    @Mock
    private PropertyPhotoMapper photoMapper;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private PropertyPhotoService propertyPhotoService;

    private UUID tenantId;
    private UUID userId;
    private Property property;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();

        property = new Property();
        property.setId(UUID.randomUUID());
        property.setTenantId(tenantId);

        // Inject @Value-wired fields
        ReflectionTestUtils.setField(propertyPhotoService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(propertyPhotoService, "presignedUrlExpiryMinutes", 15);
        ReflectionTestUtils.setField(propertyPhotoService, "maxUploadSizeBytes", 10_485_760L);
    }

    @Test
    void generateUploadUrl_ReturnsPresignedUrlAndScopedKey() throws Exception {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(propertyRepository.findByIdAndTenantId(property.getId(), tenantId))
                .thenReturn(Optional.of(property));

        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://s3.local/test-bucket/some-key"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presigned);

        GenerateUploadUrlRequest request = new GenerateUploadUrlRequest(
                "vacation pic.jpg", "image/jpeg", 500_000L);

        GenerateUploadUrlResponse response = propertyPhotoService.generateUploadUrl(
                property.getId(), request);

        assertThat(response.uploadUrl()).isEqualTo("https://s3.local/test-bucket/some-key");
        assertThat(response.httpMethod()).isEqualTo("PUT");
        assertThat(response.s3Key())
                .startsWith("tenants/" + tenantId + "/properties/" + property.getId() + "/photos/")
                .endsWith("vacation_pic.jpg");
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    void generateUploadUrl_OverSizeLimit_ThrowsConflict() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(propertyRepository.findByIdAndTenantId(property.getId(), tenantId))
                .thenReturn(Optional.of(property));

        GenerateUploadUrlRequest request = new GenerateUploadUrlRequest(
                "huge.jpg", "image/jpeg", 20_000_000L);

        assertThatThrownBy(() -> propertyPhotoService.generateUploadUrl(property.getId(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("maximum upload size");

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void generateUploadUrl_PropertyNotFound_Throws() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        UUID missing = UUID.randomUUID();
        when(propertyRepository.findByIdAndTenantId(missing, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> propertyPhotoService.generateUploadUrl(missing,
                new GenerateUploadUrlRequest("a.jpg", "image/jpeg", 100L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmUpload_ValidKey_PersistsAndAudits() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(currentUser.getUserId()).thenReturn(userId);
        when(propertyRepository.findByIdAndTenantId(property.getId(), tenantId))
                .thenReturn(Optional.of(property));
        when(photoRepository.countByPropertyId(property.getId())).thenReturn(2L);
        when(photoRepository.save(any(PropertyPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(photoMapper.toResponse(any(PropertyPhoto.class))).thenAnswer(inv -> {
            PropertyPhoto p = inv.getArgument(0);
            return new PropertyPhotoResponse(p.getId(), p.getPropertyId(), p.getS3Key(),
                    p.getFilename(), p.getContentType(), p.getSizeBytes(), p.getSortOrder(),
                    p.getCaption(), null);
        });

        String validKey = "tenants/" + tenantId + "/properties/" + property.getId()
                + "/photos/abc-vacation.jpg";
        ConfirmPhotoUploadRequest request = new ConfirmPhotoUploadRequest(
                validKey, "vacation.jpg", "image/jpeg", 250_000L, "Sunset view");

        PropertyPhotoResponse response = propertyPhotoService.confirmUpload(property.getId(), request);

        assertThat(response.sortOrder()).isEqualTo(2); // appended at the end
        assertThat(response.s3Key()).isEqualTo(validKey);
        assertThat(response.caption()).isEqualTo("Sunset view");
    }

    @Test
    void confirmUpload_KeyForDifferentProperty_ThrowsConflict() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(propertyRepository.findByIdAndTenantId(property.getId(), tenantId))
                .thenReturn(Optional.of(property));

        // Attacker-supplied key targets another property
        UUID otherProperty = UUID.randomUUID();
        String hostileKey = "tenants/" + tenantId + "/properties/" + otherProperty
                + "/photos/steal.jpg";
        ConfirmPhotoUploadRequest request = new ConfirmPhotoUploadRequest(
                hostileKey, "x.jpg", "image/jpeg", 1L, null);

        assertThatThrownBy(() -> propertyPhotoService.confirmUpload(property.getId(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("does not belong");

        verify(photoRepository, never()).save(any());
    }

    @Test
    void reorderPhotos_MismatchedListSize_ThrowsConflict() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(propertyRepository.findByIdAndTenantId(property.getId(), tenantId))
                .thenReturn(Optional.of(property));
        // Two photos in DB, but request only has one
        when(photoRepository.findByPropertyIdOrderBySortOrderAsc(property.getId()))
                .thenReturn(List.of(newPhoto(0), newPhoto(1)));

        ReorderPhotosRequest request = new ReorderPhotosRequest(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> propertyPhotoService.reorderPhotos(property.getId(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("exactly all photo IDs");
    }

    @Test
    void reorderPhotos_ValidList_UpdatesSortOrderForAll() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(currentUser.getUserId()).thenReturn(userId);
        when(propertyRepository.findByIdAndTenantId(property.getId(), tenantId))
                .thenReturn(Optional.of(property));

        PropertyPhoto p1 = newPhoto(0);
        PropertyPhoto p2 = newPhoto(1);
        PropertyPhoto p3 = newPhoto(2);
        when(photoRepository.findByPropertyIdOrderBySortOrderAsc(property.getId()))
                .thenReturn(List.of(p1, p2, p3));
        when(photoMapper.toResponse(any(PropertyPhoto.class))).thenAnswer(inv -> {
            PropertyPhoto pp = inv.getArgument(0);
            return new PropertyPhotoResponse(pp.getId(), pp.getPropertyId(), pp.getS3Key(),
                    null, null, null, pp.getSortOrder(), null, null);
        });

        // Reverse order: p3, p2, p1
        ReorderPhotosRequest request = new ReorderPhotosRequest(
                List.of(p3.getId(), p2.getId(), p1.getId()));

        List<PropertyPhotoResponse> result =
                propertyPhotoService.reorderPhotos(property.getId(), request);

        assertThat(p3.getSortOrder()).isEqualTo(0);
        assertThat(p2.getSortOrder()).isEqualTo(1);
        assertThat(p1.getSortOrder()).isEqualTo(2);
        assertThat(result).hasSize(3);
        // Response is sorted by new sort_order
        assertThat(result.get(0).sortOrder()).isEqualTo(0);
    }

    @Test
    void deletePhoto_RemovesS3ObjectAndDbRow() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(currentUser.getUserId()).thenReturn(userId);
        PropertyPhoto photo = newPhoto(0);
        when(photoRepository.findByIdAndPropertyIdAndTenantId(photo.getId(), property.getId(), tenantId))
                .thenReturn(Optional.of(photo));

        propertyPhotoService.deletePhoto(property.getId(), photo.getId());

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo(photo.getS3Key());
        verify(photoRepository).delete(photo);
    }

    @Test
    void deletePhoto_S3FailureDoesNotAbort() {
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(currentUser.getUserId()).thenReturn(userId);
        PropertyPhoto photo = newPhoto(0);
        when(photoRepository.findByIdAndPropertyIdAndTenantId(photo.getId(), property.getId(), tenantId))
                .thenReturn(Optional.of(photo));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 down"));

        // Should NOT throw — DB row still deleted even if S3 unreachable
        propertyPhotoService.deletePhoto(property.getId(), photo.getId());

        verify(photoRepository).delete(photo);
    }

    @Test
    void sanitizeFilename_StripsUnsafeChars() {
        assertThat(PropertyPhotoService.sanitizeFilename("my photo!.jpg"))
                .isEqualTo("my_photo_.jpg");
        assertThat(PropertyPhotoService.sanitizeFilename(null)).isEqualTo("file");
        assertThat(PropertyPhotoService.sanitizeFilename("")).isEqualTo("file");
    }

    private PropertyPhoto newPhoto(int sortOrder) {
        PropertyPhoto p = new PropertyPhoto();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setPropertyId(property.getId());
        p.setS3Key("tenants/" + tenantId + "/properties/" + property.getId() + "/photos/p" + sortOrder);
        p.setSortOrder(sortOrder);
        return p;
    }
}
