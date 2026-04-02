package com.rental.pms.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteUserRequest(
        @NotBlank @Email String email,
        @NotBlank String roleName
) {}
