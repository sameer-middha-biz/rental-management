package com.rental.pms.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response body returned by GlobalExceptionHandler.
 *
 * @param timestamp  when the error occurred
 * @param status     HTTP status code
 * @param error      HTTP status reason phrase
 * @param message    human-readable error description
 * @param errorCode  machine-readable error code (MODULE.ENTITY.ERROR_TYPE)
 * @param path       request URI that caused the error
 * @param fieldErrors validation field errors (null unless validation error)
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String errorCode,
        String path,
        List<FieldError> fieldErrors
) {
}
