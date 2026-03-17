package com.rental.pms.common.dto;

/**
 * Generic success wrapper for API responses.
 *
 * @param success whether the operation succeeded
 * @param message human-readable status message
 * @param data    the response payload
 * @param <T>     type of the response payload
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {

    public static <T> ApiResponse<T> of(T data, String message) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, "Success", data);
    }
}
