package com.ewos.payroll.domain;

/**
 * Payment instruction lifecycle: {@code PENDING} on generation; {@code PAID} once the bank settles
 * (settlement_reference required); {@code FAILED} on reject (failure_reason required); {@code
 * SKIPPED} for rows where the employee has no primary bank account or the amount is zero.
 */
public enum PaymentInstructionStatus {
    PENDING,
    PAID,
    FAILED,
    SKIPPED
}
