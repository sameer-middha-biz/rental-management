package com.rental.pms.modules.guest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGuestRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Size(min = 3, max = 3, message = "nationality must be ISO 3166-1 alpha-3 (3 letters)") String nationality,
        @Size(max = 500) String idDocumentS3Key,
        String notes
) {
}
