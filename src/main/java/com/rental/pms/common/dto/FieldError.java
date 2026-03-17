package com.rental.pms.common.dto;

/**
 * Represents a single field-level validation error.
 *
 * @param field   the field name that failed validation
 * @param message the validation error message
 */
public record FieldError(
        String field,
        String message
) {
}
