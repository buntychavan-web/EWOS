package com.ewos.payroll.domain;

/**
 * Lifecycle of a {@link FinalSettlement}:
 *
 * <ul>
 *   <li>{@code DRAFT} — being edited; not yet approved.
 *   <li>{@code APPROVED} — locked; awaiting settlement run.
 *   <li>{@code SETTLED} — settlement run generated the payslip; immutable.
 *   <li>{@code CANCELLED} — abandoned; no run generated. Allowed only from DRAFT/APPROVED.
 * </ul>
 */
public enum FinalSettlementStatus {
    DRAFT,
    APPROVED,
    SETTLED,
    CANCELLED
}
