package com.ewos.company.domain;

/**
 * Kinds of reusable policies a company can reference. The actual policy content lives in dedicated
 * tables in future sprints; this module only records the (company, policy_ref) assignment with its
 * effective window.
 */
public enum PolicyType {
    HOLIDAY_CALENDAR,
    PAYROLL_CALENDAR,
    LEAVE_POLICY,
    ATTENDANCE_POLICY,
    SHIFT_POLICY,
    WORKFLOW
}
