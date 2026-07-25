package com.ewos.dataexchange.domain;

/** Lifecycle status of a {@link DataExchangeRecord}. */
public enum DataExchangeStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    RETRY,
    ACKNOWLEDGED,
    CANCELLED
}
