package com.ewos.dataexchange.domain;

import com.ewos.shared.exception.ApiException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Framework-neutral rule enforcer for {@link DataExchangeRecord} lifecycle transitions. Mirrors the
 * {@code PayrollPolicy} / {@code WorkflowTransitionPolicy} convention: every mutation goes through
 * the matching {@code assert...} helper so the guard set stays in one place.
 */
@Component
public final class DataExchangePolicy {

    private static final Set<DataExchangeStatus> CANCELLABLE =
            Set.of(
                    DataExchangeStatus.PENDING,
                    DataExchangeStatus.PROCESSING,
                    DataExchangeStatus.FAILED,
                    DataExchangeStatus.RETRY);

    public void assertStartable(DataExchangeRecord record) {
        if (record.getStatus() != DataExchangeStatus.PENDING
                && record.getStatus() != DataExchangeStatus.RETRY) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Record is "
                            + record.getStatus()
                            + "; only PENDING or RETRY records can start processing");
        }
    }

    public void assertProcessing(DataExchangeRecord record) {
        if (record.getStatus() != DataExchangeStatus.PROCESSING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Record is " + record.getStatus() + "; expected PROCESSING");
        }
    }

    public void assertRetryable(DataExchangeRecord record) {
        if (record.getStatus() != DataExchangeStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only FAILED records can be retried");
        }
    }

    public void assertAcknowledgeable(DataExchangeRecord record) {
        if (record.getStatus() != DataExchangeStatus.SUCCESS) {
            throw new ApiException(HttpStatus.CONFLICT, "Only SUCCESS records can be acknowledged");
        }
    }

    public void assertCancellable(DataExchangeRecord record) {
        if (!CANCELLABLE.contains(record.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Record is " + record.getStatus() + "; cannot be cancelled");
        }
    }
}
