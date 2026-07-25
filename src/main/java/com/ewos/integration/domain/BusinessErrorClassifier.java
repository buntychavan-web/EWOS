package com.ewos.integration.domain;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Sprint 14.4 — Business Error Classification. Maps a raw failure from an {@link
 * IntegrationAdapter} into an {@link ErrorClassification} so the Monitoring and Operations
 * dashboards can group by cause instead of free-text messages, and so retry decisions are
 * cause-driven rather than blanket.
 */
@Component
public class BusinessErrorClassifier {

    public ErrorClassification classify(Throwable error) {
        if (error instanceof HttpClientErrorException httpEx) {
            return httpEx.getStatusCode().value() == 401 || httpEx.getStatusCode().value() == 403
                    ? ErrorClassification.AUTHENTICATION
                    : ErrorClassification.VALIDATION;
        }
        if (error instanceof HttpServerErrorException) {
            return ErrorClassification.EXTERNAL_SYSTEM;
        }
        if (error instanceof SocketTimeoutException || error instanceof UnknownHostException) {
            return ErrorClassification.TRANSIENT_NETWORK;
        }
        if (error instanceof ResourceAccessException) {
            return ErrorClassification.TRANSIENT_NETWORK;
        }
        if (error instanceof IOException) {
            return ErrorClassification.EXTERNAL_SYSTEM;
        }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            return ErrorClassification.CONFIGURATION;
        }
        return ErrorClassification.UNKNOWN;
    }
}
