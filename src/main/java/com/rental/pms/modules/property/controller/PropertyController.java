package com.rental.pms.modules.property.controller;

import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.modules.property.dto.ConfirmPhotoUploadRequest;
import com.rental.pms.modules.property.dto.CreatePropertyRequest;
import com.rental.pms.modules.property.dto.GenerateUploadUrlRequest;
import com.rental.pms.modules.property.dto.GenerateUploadUrlResponse;
import com.rental.pms.modules.property.dto.PropertyPhotoResponse;
import com.rental.pms.modules.property.dto.PropertyResponse;
import com.rental.pms.modules.property.dto.ReorderPhotosRequest;
import com.rental.pms.modules.property.dto.UpdatePropertyRequest;
import com.rental.pms.modules.property.entity.PropertyStatus;
import com.rental.pms.modules.property.entity.PropertyType;
import com.rental.pms.modules.property.service.PropertyPhotoService;
import com.rental.pms.modules.property.service.PropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@Tag(name = "Properties", description = "Manage tenant properties/listings")
public class PropertyController {

    private final PropertyService propertyService;
    private final PropertyPhotoService propertyPhotoService;

    @PostMapping
    @PreAuthorize("hasAuthority('PROPERTY_CREATE')")
    @Operation(summary = "Create a new property")
    public ResponseEntity<PropertyResponse> create(@Valid @RequestBody CreatePropertyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROPERTY_VIEW')")
    @Operation(summary = "List properties (optional filters: status, propertyType, search)")
    public ResponseEntity<PageResponse<PropertyResponse>> list(
            @RequestParam(required = false) PropertyStatus status,
            @RequestParam(required = false) PropertyType propertyType,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(propertyService.search(status, propertyType, search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROPERTY_VIEW')")
    @Operation(summary = "Get property by ID")
    public ResponseEntity<PropertyResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(propertyService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROPERTY_EDIT')")
    @Operation(summary = "Update a property (partial — null fields ignored)")
    public ResponseEntity<PropertyResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdatePropertyRequest request) {
        return ResponseEntity.ok(propertyService.update(id, request));
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('PROPERTY_EDIT')")
    @Operation(summary = "Archive a property (soft delete)")
    public ResponseEntity<PropertyResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(propertyService.archive(id));
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('PROPERTY_EDIT')")
    @Operation(summary = "Restore an archived property")
    public ResponseEntity<PropertyResponse> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(propertyService.restore(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PROPERTY_DELETE')")
    @Operation(summary = "Permanently delete a property")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        propertyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Photos ---

    @PostMapping("/{id}/photos/upload-url")
    @PreAuthorize("hasAuthority('PROPERTY_EDIT')")
    @Operation(summary = "Generate a pre-signed S3 PUT URL for uploading a photo")
    public ResponseEntity<GenerateUploadUrlResponse> generateUploadUrl(
            @PathVariable UUID id,
            @Valid @RequestBody GenerateUploadUrlRequest request) {
        return ResponseEntity.ok(propertyPhotoService.generateUploadUrl(id, request));
    }

    @PostMapping("/{id}/photos")
    @PreAuthorize("hasAuthority('PROPERTY_EDIT')")
    @Operation(summary = "Confirm a photo upload (create DB record after S3 PUT)")
    public ResponseEntity<PropertyPhotoResponse> confirmPhotoUpload(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmPhotoUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(propertyPhotoService.confirmUpload(id, request));
    }

    @GetMapping("/{id}/photos")
    @PreAuthorize("hasAuthority('PROPERTY_VIEW')")
    @Operation(summary = "List photos for a property, ordered by sort_order")
    public ResponseEntity<List<PropertyPhotoResponse>> listPhotos(@PathVariable UUID id) {
        return ResponseEntity.ok(propertyPhotoService.listPhotos(id));
    }

    @PutMapping("/{id}/photos/reorder")
    @PreAuthorize("hasAuthority('PROPERTY_EDIT')")
    @Operation(summary = "Reorder all photos for a property (full-list reorder)")
    public ResponseEntity<List<PropertyPhotoResponse>> reorderPhotos(
            @PathVariable UUID id,
            @Valid @RequestBody ReorderPhotosRequest request) {
        return ResponseEntity.ok(propertyPhotoService.reorderPhotos(id, request));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize("hasAuthority('PROPERTY_EDIT')")
    @Operation(summary = "Delete a property photo (removes DB row + S3 object)")
    public ResponseEntity<Void> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        propertyPhotoService.deletePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }
}
