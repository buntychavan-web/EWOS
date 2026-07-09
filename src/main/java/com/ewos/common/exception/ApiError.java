package com.ewos.common.exception;

import com.ewos.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;

/**
 * Uniform error envelope returned by every 4xx / 5xx response the API emits. The {@code
 * correlationId} matches the {@code X-Request-ID} response header and the log entries produced
 * during the request.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        int status,
        String error,
        String message,
        List<String> details,
        String path,
        Instant timestamp,
        String correlationId) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(
                status, error, message, List.of(), path, Instant.now(), currentCorrelationId());
    }

    public static ApiError of(
            int status, String error, String message, List<String> details, String path) {
        return new ApiError(
                status, error, message, details, path, Instant.now(), currentCorrelationId());
    }

    private static String currentCorrelationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
