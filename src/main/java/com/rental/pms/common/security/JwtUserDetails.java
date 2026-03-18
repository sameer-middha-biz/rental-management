package com.rental.pms.common.security;

import java.util.List;
import java.util.UUID;

/**
 * Immutable record holding JWT-extracted user details.
 * Set as the Authentication principal by JwtAuthenticationFilter.
 */
public record JwtUserDetails(
        UUID userId,
        UUID tenantId,
        List<String> roles,
        List<String> permissions
) {
}
