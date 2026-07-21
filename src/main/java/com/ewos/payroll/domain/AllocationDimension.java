package com.ewos.payroll.domain;

/**
 * Cost-tracking dimension against which a {@link GLMapping} splits its journal lines. {@code NONE}
 * — no split; {@code COST_CENTRE} / {@code BUSINESS_UNIT} — read from the employee's active {@link
 * EmployeeCostAllocation}; {@code DEPARTMENT} — read from the employee's primary organization unit.
 */
public enum AllocationDimension {
    NONE,
    COST_CENTRE,
    BUSINESS_UNIT,
    DEPARTMENT
}
