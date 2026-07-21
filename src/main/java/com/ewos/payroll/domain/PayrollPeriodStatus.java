package com.ewos.payroll.domain;

/**
 * Lifecycle of a {@link PayrollPeriod}:
 *
 * <ul>
 *   <li>{@code OPEN} — timesheet / leave data still accepted; no runs allowed.
 *   <li>{@code LOCKED} — data frozen; runs allowed.
 *   <li>{@code CLOSED} — finalized; no runs / no edits.
 * </ul>
 */
public enum PayrollPeriodStatus {
    OPEN,
    LOCKED,
    CLOSED
}
