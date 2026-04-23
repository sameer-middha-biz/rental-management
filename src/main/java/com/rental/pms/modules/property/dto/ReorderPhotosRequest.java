package com.rental.pms.modules.property.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * @param photoIds ordered list of photo IDs; the position in the list becomes {@code sort_order}.
 */
public record ReorderPhotosRequest(
        @NotEmpty List<UUID> photoIds
) {
}
