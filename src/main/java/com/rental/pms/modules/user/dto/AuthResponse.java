package com.rental.pms.modules.user.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
