package com.ewos.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central error-to-{@link ApiError} translation. Every handler returns the same envelope; the
 * frontend consumes exactly one shape.
 *
 * <p>WP-002A additions:
 *
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} now populates structured {@code fieldErrors}
 *       instead of flat {@code details} strings.
 *   <li>{@link ConstraintViolationException} — path / query / bean-level violations outside
 *       {@code @RequestBody}.
 *   <li>{@link MissingRequestHeaderException} — missing required header.
 *   <li>{@link HttpMediaTypeNotSupportedException} — wrong {@code Content-Type}.
 *   <li>{@link DataIntegrityViolationException} — unique-constraint / FK violations from the DB.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Fields we never want to echo back to the caller, even in a validation-error payload. */
    private static final List<String> SENSITIVE_FIELDS =
            List.of("password", "currentPassword", "newPassword", "secret", "token");

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(
            ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(
                        ApiError.of(
                                ex.getStatus().value(),
                                ex.getStatus().getReasonPhrase(),
                                ex.getMessage(),
                                request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldViolation> fieldErrors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                f ->
                                        new FieldViolation(
                                                f.getField(),
                                                sensitize(f.getField(), f.getRejectedValue()),
                                                f.getDefaultMessage(),
                                                f.getCode()))
                        .toList();
        return ResponseEntity.badRequest()
                .body(
                        ApiError.validation(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                "Validation failed",
                                fieldErrors,
                                request.getRequestURI()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldViolation> fieldErrors =
                ex.getConstraintViolations().stream()
                        .map(
                                v ->
                                        new FieldViolation(
                                                v.getPropertyPath() == null
                                                        ? null
                                                        : v.getPropertyPath().toString(),
                                                sensitize(
                                                        v.getPropertyPath() == null
                                                                ? null
                                                                : v.getPropertyPath().toString(),
                                                        v.getInvalidValue()),
                                                v.getMessage(),
                                                v.getConstraintDescriptor() == null
                                                        ? null
                                                        : v.getConstraintDescriptor()
                                                                .getAnnotation()
                                                                .annotationType()
                                                                .getSimpleName()))
                        .toList();
        return ResponseEntity.badRequest()
                .body(
                        ApiError.validation(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                "Constraint violation",
                                fieldErrors,
                                request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        ApiError.of(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                "Request body is missing or malformed",
                                request.getRequestURI()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        ApiError.of(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                "Missing required parameter '" + ex.getParameterName() + "'",
                                request.getRequestURI()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        ApiError.of(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                "Missing required header '" + ex.getHeaderName() + "'",
                                request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Class<?> required = ex.getRequiredType();
        String expected = required != null ? required.getSimpleName() : "value";
        return ResponseEntity.badRequest()
                .body(
                        ApiError.of(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                "Parameter '" + ex.getName() + "' must be a " + expected,
                                request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(
                        ApiError.of(
                                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                                HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(),
                                "Content type "
                                        + (ex.getContentType() == null ? "?" : ex.getContentType())
                                        + " is not supported for this endpoint",
                                request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(
                        ApiError.of(
                                HttpStatus.METHOD_NOT_ALLOWED.value(),
                                HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
                                "HTTP method "
                                        + ex.getMethod()
                                        + " is not supported for this endpoint",
                                request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                HttpStatus.NOT_FOUND.value(),
                                HttpStatus.NOT_FOUND.getReasonPhrase(),
                                "Resource not found",
                                request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        // Do not leak SQL fragments to the client; log the full exception and return a
        // conservative message. Downstream callers key off the correlation id in the response.
        log.info(
                "DB constraint violation at {}: {}",
                request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiError.of(
                                HttpStatus.CONFLICT.value(),
                                HttpStatus.CONFLICT.getReasonPhrase(),
                                "Data integrity violation — a required uniqueness or FK constraint"
                                        + " was violated",
                                request.getRequestURI()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Optimistic lock conflict at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiError.of(
                                HttpStatus.CONFLICT.value(),
                                HttpStatus.CONFLICT.getReasonPhrase(),
                                "Concurrent modification detected — refresh and retry",
                                request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(
                        ApiError.of(
                                HttpStatus.UNAUTHORIZED.value(),
                                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                                ex.getMessage(),
                                request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(
                        ApiError.of(
                                HttpStatus.FORBIDDEN.value(),
                                HttpStatus.FORBIDDEN.getReasonPhrase(),
                                ex.getMessage(),
                                request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiError.of(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                                "Unexpected error",
                                request.getRequestURI()));
    }

    private static Object sensitize(String field, Object value) {
        if (field == null || value == null) {
            return value;
        }
        String lower = field.toLowerCase(java.util.Locale.ROOT);
        for (String s : SENSITIVE_FIELDS) {
            if (lower.contains(s)) {
                return null;
            }
        }
        return value;
    }
}
