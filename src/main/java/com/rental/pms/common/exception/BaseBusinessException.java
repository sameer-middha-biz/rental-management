package com.rental.pms.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Abstract base for all domain/business exceptions.
 * Each subclass provides a specific errorCode and httpStatus.
 * The GlobalExceptionHandler translates these into ErrorResponse payloads.
 */
@Getter
public abstract class BaseBusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected BaseBusinessException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected BaseBusinessException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
