package com.ewos.attendance.domain;

/** Lifecycle status of a {@link Timesheet}. */
public enum TimesheetStatus {
    /** Editable by the employee; not visible to approvers. */
    DRAFT,
    /** Submitted for approval; workflow instance is running. */
    SUBMITTED,
    /** Approved by manager; feeds Payroll. */
    APPROVED,
    /** Rejected by manager; back to employee with a reason for correction. */
    REJECTED,
    /** Voided (e.g. employee terminated mid-period). */
    CANCELLED
}
