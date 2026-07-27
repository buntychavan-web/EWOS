package com.ewos.integration.domain;

/**
 * Business error taxonomy for a failed {@link IntegrationExecutionRecord}. Lets the Integration
 * Monitoring Dashboard and Operations Dashboard group failures by cause rather than raw exception
 * text, and lets {@link com.ewos.integration.application.IntegrationExecutionService} decide which
 * failures are worth an automatic {@code RETRY} (see {@link #retryable()}).
 */
public enum ErrorClassification {
    /** The payload itself is malformed or fails a business rule — retrying won't help. */
    VALIDATION(false),
    /** The adapter's credentials were rejected by the external system. */
    AUTHENTICATION(false),
    /** A transport-level failure (timeout, connection reset, DNS) — likely to succeed on retry. */
    TRANSIENT_NETWORK(true),
    /** The payload couldn't be mapped into the external system's expected shape. */
    DATA_MAPPING(false),
    /** The external system itself reported an error (5xx, SFTP server error, ...). */
    EXTERNAL_SYSTEM(true),
    /** The {@link IntegrationConfiguration} is missing, inactive, or internally inconsistent. */
    CONFIGURATION(false),
    /** Uncategorised. */
    UNKNOWN(false);

    private final boolean retryable;

    ErrorClassification(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
