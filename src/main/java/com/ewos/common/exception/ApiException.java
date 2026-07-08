package com.ewos.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base runtime exception for expected application errors. Carries an HTTP status
 * so the global handler can translate it directly.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
