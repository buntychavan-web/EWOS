package com.ewos.payroll.domain;

/** Widens {@link GLMappingSourceKind} with {@code BALANCING} for the auto-generated NET line. */
public enum PayrollJournalLineSourceKind {
    PAY_COMPONENT,
    EMPLOYER_CONTRIBUTION,
    NET_PAY,
    PROVISION,
    ACCRUAL,
    STATUTORY,
    BALANCING
}
