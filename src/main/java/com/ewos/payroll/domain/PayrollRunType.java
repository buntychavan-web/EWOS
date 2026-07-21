package com.ewos.payroll.domain;

/**
 * How a {@link PayrollRun} came into existence.
 *
 * <ul>
 *   <li>{@code REGULAR} — scheduled run over a locked period (WP-009 default).
 *   <li>{@code SUPPLEMENTARY} — off-cycle run that only processes selected employees (bonuses,
 *       retro corrections, mid-cycle adjustments).
 *   <li>{@code FINAL_SETTLEMENT} — one-off payslip for a terminated employee, generated from a
 *       {@link FinalSettlement} record.
 * </ul>
 */
public enum PayrollRunType {
    REGULAR,
    SUPPLEMENTARY,
    FINAL_SETTLEMENT
}
