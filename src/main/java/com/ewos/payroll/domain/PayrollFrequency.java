package com.ewos.payroll.domain;

/**
 * Pay-period cadence. Drives period-length math on {@link PayrollPeriod} and salary-per-period math
 * on {@link EmployeeCompensation}.
 */
public enum PayrollFrequency {
    MONTHLY,
    SEMI_MONTHLY,
    BI_WEEKLY,
    WEEKLY
}
