package com.ewos.employee.domain;

/**
 * Lifecycle status of an {@link Employee}.
 *
 * <p>Persisted as {@code VARCHAR} — see the check constraint in {@code V10__employee_engine.sql}.
 */
public enum EmployeeStatus {
    /** Currently employed and eligible for all HR flows. */
    ACTIVE,

    /**
     * On approved leave (parental, sabbatical, medical, ...). Payroll may still pay; access is
     * often retained but reduced.
     */
    ON_LEAVE,

    /**
     * Access suspended pending investigation. Payroll may still be running while an HR / legal
     * process resolves the underlying incident.
     */
    SUSPENDED,

    /**
     * End-of-employment. {@code termination_date} is set; the row is retained for historical
     * reporting and compliance. No further mutation apart from soft-delete cleanup.
     */
    TERMINATED
}
