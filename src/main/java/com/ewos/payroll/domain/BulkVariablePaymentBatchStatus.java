package com.ewos.payroll.domain;

/** Outcome of a bulk variable-payment upload attempt. */
public enum BulkVariablePaymentBatchStatus {
    /** Every row validated; one {@link PayrollArrear} was created per row. */
    COMMITTED,
    /** At least one row failed validation; nothing in the batch was written. */
    REJECTED
}
