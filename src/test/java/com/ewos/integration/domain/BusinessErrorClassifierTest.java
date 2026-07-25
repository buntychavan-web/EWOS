package com.ewos.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class BusinessErrorClassifierTest {

    private final BusinessErrorClassifier classifier = new BusinessErrorClassifier();

    @Test
    void classifiesUnauthorizedAsAuthentication() {
        HttpClientErrorException error =
                HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);

        assertThat(classifier.classify(error)).isEqualTo(ErrorClassification.AUTHENTICATION);
    }

    @Test
    void classifiesForbiddenAsAuthentication() {
        HttpClientErrorException error =
                HttpClientErrorException.create(
                        HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, new byte[0], null);

        assertThat(classifier.classify(error)).isEqualTo(ErrorClassification.AUTHENTICATION);
    }

    @Test
    void classifiesOtherClientErrorsAsValidation() {
        HttpClientErrorException error =
                HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);

        assertThat(classifier.classify(error)).isEqualTo(ErrorClassification.VALIDATION);
    }

    @Test
    void classifiesServerErrorsAsExternalSystem() {
        HttpServerErrorException error =
                HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", HttpHeaders.EMPTY, new byte[0], null);

        assertThat(classifier.classify(error)).isEqualTo(ErrorClassification.EXTERNAL_SYSTEM);
    }

    @Test
    void classifiesTimeoutsAsTransientNetwork() {
        assertThat(classifier.classify(new SocketTimeoutException("timed out")))
                .isEqualTo(ErrorClassification.TRANSIENT_NETWORK);
    }

    @Test
    void classifiesUnknownHostAsTransientNetwork() {
        assertThat(classifier.classify(new UnknownHostException("no such host")))
                .isEqualTo(ErrorClassification.TRANSIENT_NETWORK);
    }

    @Test
    void classifiesResourceAccessExceptionAsTransientNetwork() {
        assertThat(classifier.classify(new ResourceAccessException("connection refused")))
                .isEqualTo(ErrorClassification.TRANSIENT_NETWORK);
    }

    @Test
    void classifiesGenericIoAsExternalSystem() {
        assertThat(classifier.classify(new IOException("disk full")))
                .isEqualTo(ErrorClassification.EXTERNAL_SYSTEM);
    }

    @Test
    void classifiesIllegalArgumentAsConfiguration() {
        assertThat(classifier.classify(new IllegalArgumentException("bad config")))
                .isEqualTo(ErrorClassification.CONFIGURATION);
    }

    @Test
    void classifiesIllegalStateAsConfiguration() {
        assertThat(classifier.classify(new IllegalStateException("bad state")))
                .isEqualTo(ErrorClassification.CONFIGURATION);
    }

    @Test
    void classifiesUnrecognizedThrowablesAsUnknown() {
        assertThat(classifier.classify(new RuntimeException("mystery")))
                .isEqualTo(ErrorClassification.UNKNOWN);
    }
}
