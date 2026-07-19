package com.ewos.common.exception;

import com.ewos.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;

/**
 * Uniform error envelope returned by every 4xx / 5xx response the API emits.
 *
 * <p>Contract (WP-002A, "API response standardization"):
 *
 * <ul>
 *   <li>{@code status} — the HTTP status code, always populated.
 *   <li>{@code error} — the HTTP reason phrase ({@code "Bad Request"}).
 *   <li>{@code message} — a single human-readable sentence safe to show a user.
 *   <li>{@code details} — free-form supplemental strings (rare; prefer {@code fieldErrors}).
 *   <li>{@code fieldErrors} — structured per-field validation errors. The FE maps these 1:1 to form
 *       fields; the array is empty for non-validation errors.
 *   <li>{@code path} — the request URI, so downstream tooling can group by endpoint.
 *   <li>{@code timestamp} — ISO-8601 UTC.
 *   <li>{@code correlationId} — matches the {@code X-Request-ID} response header + log lines.
 * </ul>
 *
 * <p>The envelope is emitted with {@code JsonInclude.NON_EMPTY}, so absent optional fields are
 * omitted from the JSON. Consumers must therefore treat {@code details} / {@code fieldErrors} as
 * optionally-empty and never assume presence.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Uniform error envelope emitted on every non-2xx response")
public record ApiError(
        int status,
        String error,
        String message,
        List<String> details,
        List<FieldViolation> fieldErrors,
        String path,
        Instant timestamp,
        String correlationId) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(
                status,
                error,
                message,
                List.of(),
                List.of(),
                path,
                Instant.now(),
                currentCorrelationId());
    }

    public static ApiError of(
            int status, String error, String message, List<String> details, String path) {
        return new ApiError(
                status,
                error,
                message,
                details,
                List.of(),
                path,
                Instant.now(),
                currentCorrelationId());
    }

    public static ApiError validation(
            int status,
            String error,
            String message,
            List<FieldViolation> fieldErrors,
            String path) {
        return new ApiError(
                status,
                error,
                message,
                List.of(),
                fieldErrors,
                path,
                Instant.now(),
                currentCorrelationId());
    }

    private static String currentCorrelationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
