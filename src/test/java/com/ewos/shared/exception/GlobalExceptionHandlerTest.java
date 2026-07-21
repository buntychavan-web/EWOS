package com.ewos.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.PropertyValues;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = mockRequest("/api/test");

    @Test
    void apiExceptionMapsToItsOwnStatus() {
        ResponseEntity<ApiError> res =
                handler.handleApiException(
                        new ApiException(HttpStatus.CONFLICT, "duplicate"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().message()).isEqualTo("duplicate");
    }

    @Test
    void validationMapsToStructuredFieldErrors() {
        Person p = new Person();
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(p, "person");
        binding.rejectValue("email", "Email", "must be a well-formed email address");
        binding.addError(
                new org.springframework.validation.FieldError(
                        "person",
                        "password",
                        "secret-oops",
                        false,
                        new String[] {"Size"},
                        null,
                        "size must be at least 8"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<ApiError> res = handler.handleValidation(ex, request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().fieldErrors()).hasSize(2);
        // Sensitive field's rejectedValue must be scrubbed.
        FieldViolation passwordError =
                res.getBody().fieldErrors().stream()
                        .filter(f -> "password".equals(f.field()))
                        .findFirst()
                        .orElseThrow();
        assertThat(passwordError.rejectedValue()).isNull();
    }

    @Test
    void malformedBodyReturns400() {
        ResponseEntity<ApiError> res =
                handler.handleMalformedBody(
                        new HttpMessageNotReadableException(
                                "bad", new ServletServerHttpRequest(new MockHttpServletRequest())),
                        request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingParamMentionsTheParameter() {
        ResponseEntity<ApiError> res =
                handler.handleMissingParam(
                        new MissingServletRequestParameterException("id", "String"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).contains("id");
    }

    @Test
    void typeMismatchReturns400() {
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException(
                        "not-a-uuid", java.util.UUID.class, "id", null, new RuntimeException());
        ResponseEntity<ApiError> res = handler.handleTypeMismatch(ex, request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).contains("id").contains("UUID");
    }

    @Test
    void unsupportedMediaTypeReturns415() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, java.util.List.of());
        ResponseEntity<ApiError> res = handler.handleUnsupportedMediaType(ex, request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void methodNotSupportedReturns405() {
        ResponseEntity<ApiError> res =
                handler.handleMethodNotSupported(
                        new HttpRequestMethodNotSupportedException("PATCH"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void noResourceFoundReturns404() {
        ResponseEntity<ApiError> res =
                handler.handleNoResource(
                        new NoResourceFoundException(
                                org.springframework.http.HttpMethod.GET, "/api/missing"),
                        request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void dataIntegrityViolationReturns409() {
        ResponseEntity<ApiError> res =
                handler.handleDataIntegrity(
                        new DataIntegrityViolationException(
                                "duplicate key value violates unique constraint",
                                new RuntimeException("underlying")),
                        request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void optimisticLockReturns409() {
        ResponseEntity<ApiError> res =
                handler.handleOptimisticLock(
                        new OptimisticLockingFailureException("stale"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void authenticationReturns401() {
        ResponseEntity<ApiError> res =
                handler.handleAuthentication(new BadCredentialsException("bad"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void accessDeniedReturns403() {
        ResponseEntity<ApiError> res =
                handler.handleAccessDenied(new AccessDeniedException("nope"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unexpectedReturns500() {
        ResponseEntity<ApiError> res =
                handler.handleUnexpected(new RuntimeException("boom"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static HttpServletRequest mockRequest(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        return r;
    }

    // For MethodArgumentNotValidException binding-result plumbing.
    static class Person {
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public String getPassword() {
            return password;
        }

        void applyPropertyValues(PropertyValues pv) {
            // needed by BeanPropertyBindingResult
        }
    }
}
