package com.rental.pms.modules.property.dto;

import java.time.Instant;
import java.util.UUID;

public record PropertyPhotoResponse(
        UUID id,
        UUID propertyId,
        String s3Key,
        String filename,
        String contentType,
        Long sizeBytes,
        Integer sortOrder,
        String caption,
        Instant createdAt
) {
}
