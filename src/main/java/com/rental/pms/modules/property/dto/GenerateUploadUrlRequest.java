package com.rental.pms.modules.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GenerateUploadUrlRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank
        @Pattern(regexp = "image/(jpeg|jpg|png|webp|gif)",
                message = "contentType must be one of image/jpeg, image/png, image/webp, image/gif")
        String contentType,
        @Positive Long sizeBytes
) {
}
