package com.ewos.payroll.domain;

/**
 * A payslip is {@code DRAFT} while its parent {@link PayrollRun} is being processed. Once the run
 * is finalized every payslip flips to {@code FINALIZED} and becomes immutable.
 */
public enum PayslipStatus {
    DRAFT,
    FINALIZED
}
