package com.rental.pms.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|DISABLED", message = "Status must be ACTIVE or DISABLED")
        String status
) {}
