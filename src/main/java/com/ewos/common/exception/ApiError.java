package com.ewos.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        int status,
        String error,
        String message,
        List<String> details,
        String path,
        Instant timestamp
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(status, error, message, List.of(), path, Instant.now());
    }

    public static ApiError of(int status, String error, String message, List<String> details, String path) {
        return new ApiError(status, error, message, details, path, Instant.now());
    }
}
