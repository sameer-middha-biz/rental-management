package com.rental.pms.modules.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ConfirmPhotoUploadRequest(
        @NotBlank @Size(max = 500) String s3Key,
        @Size(max = 255) String filename,
        @Size(max = 100) String contentType,
        @PositiveOrZero Long sizeBytes,
        @Size(max = 255) String caption
) {
}
