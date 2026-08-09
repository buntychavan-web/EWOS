package com.ewos.employee.domain;

/**
 * Sprint 27C — per-direct-report attendance classification for the MSS dashboard's {@code
 * teamAttendanceSnapshot} (PRD §4.2). The attendance module has no live daily rollup (only raw
 * clock {@code TimeEntry} events and a payroll-time LOP calculator) — {@link #NOT_MARKED} is the
 * honest default for everyone not covered by an approved leave request; {@link #PRESENT} is never
 * emitted today (would require inventing a same-day rollup the module doesn't have) but stays in
 * the contract for a future increment that adds one.
 */
public enum MssAttendanceStatus {
    PRESENT,
    ON_LEAVE,
    NOT_MARKED
}
