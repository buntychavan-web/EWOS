package com.ewos.payroll.domain;

/**
 * Payroll-run lifecycle:
 *
 * <ul>
 *   <li>{@code PENDING} — persisted, not yet started.
 *   <li>{@code PROCESSING} — actively generating payslips.
 *   <li>{@code COMPLETED} — payslips generated, awaiting finalization.
 *   <li>{@code FINALIZED} — payslips locked; audit trail immutable.
 *   <li>{@code FROZEN} — terminal lock; no supplementary or corrective run may adjust this run's
 *       payslips. Only reachable from FINALIZED.
 *   <li>{@code FAILED} — processing errored; failure reason set.
 * </ul>
 */
public enum PayrollRunStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FINALIZED,
    FROZEN,
    FAILED
}
