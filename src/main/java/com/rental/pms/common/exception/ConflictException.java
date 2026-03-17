package com.rental.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request conflicts with the current state of a resource.
 * Examples: duplicate booking, optimistic lock conflict.
 * Maps to HTTP 409 Conflict.
 */
public class ConflictException extends BaseBusinessException {

    public ConflictException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.CONFLICT);
    }
}
