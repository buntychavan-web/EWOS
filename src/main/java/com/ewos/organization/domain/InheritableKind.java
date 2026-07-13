package com.ewos.organization.domain;

/**
 * Kinds of resources an organization node can inherit from its parent chain or override locally.
 * Chosen to line up with the reusable-policy vocabulary used by the Company module (Sprint 6) so
 * that inheritance can transparently fall through to the company-level assignment when no node in
 * the chain declares an override.
 */
public enum InheritableKind {
    HOLIDAY_CALENDAR,
    PAYROLL_CALENDAR,
    LEAVE_POLICY,
    ATTENDANCE_POLICY,
    SHIFT_POLICY,
    WORKFLOW,
    COST_CENTRE,
    PROFIT_CENTRE,
    STATUTORY_REGISTRATION,
    SHARED_SERVICE
}
