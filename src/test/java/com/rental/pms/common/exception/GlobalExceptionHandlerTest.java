package com.rental.pms.common.exception;

import com.rental.pms.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    private static final String REQUEST_URI = "/api/v1/properties";

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
    }

    @Test
    void handleBusinessException_WhenResourceNotFound_ShouldReturn404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Property", "id", UUID.randomUUID());

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().errorCode()).isEqualTo("PROPERTY.NOT_FOUND");
        assertThat(response.getBody().path()).isEqualTo(REQUEST_URI);
        assertThat(response.getBody().fieldErrors()).isNull();
    }

    @Test
    void handleBusinessException_WhenConflict_ShouldReturn409() {
        ConflictException ex = new ConflictException("Booking dates overlap", "BOOKING.AVAILABILITY.CONFLICT");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().errorCode()).isEqualTo("BOOKING.AVAILABILITY.CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("Booking dates overlap");
    }

    @Test
    void handleBusinessException_WhenTenantLimitExceeded_ShouldReturn409() {
        TenantLimitExceededException ex = new TenantLimitExceededException("properties", 5, 5);

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("TENANT.LIMIT.EXCEEDED");
        assertThat(response.getBody().message()).contains("properties");
    }

    @Test
    void handleValidationException_ShouldReturn400WithFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        org.springframework.validation.FieldError fieldError =
                new org.springframework.validation.FieldError("request", "checkIn", "must be a future or present date");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION.FIELD.INVALID");
        assertThat(response.getBody().fieldErrors()).hasSize(1);
        assertThat(response.getBody().fieldErrors().getFirst().field()).isEqualTo("checkIn");
    }

    @Test
    void handleIllegalArgument_ShouldReturn400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid page size");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION.ARGUMENT.INVALID");
    }

    @Test
    void handleSecurityException_ShouldReturn403() {
        SecurityException ex = new SecurityException("Cross-tenant access denied");

        ResponseEntity<ErrorResponse> response = handler.handleSecurityException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("AUTH.ACCESS.DENIED");
    }

    @Test
    void handleGenericException_ShouldReturn500() {
        Exception ex = new RuntimeException("Something broke");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("SYSTEM.INTERNAL.ERROR");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void handleBusinessException_ShouldIncludeTimestamp() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Booking", "id", UUID.randomUUID());

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
