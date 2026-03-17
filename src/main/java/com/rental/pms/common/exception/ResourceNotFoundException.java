package com.rental.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource cannot be found.
 * Maps to HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends BaseBusinessException {

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(
                String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue),
                resourceName.toUpperCase().replace(" ", "_") + ".NOT_FOUND",
                HttpStatus.NOT_FOUND
        );
    }
}
