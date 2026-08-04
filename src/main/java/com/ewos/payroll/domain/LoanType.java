package com.ewos.payroll.domain;

/**
 * What an {@link EmployeeLoan} recovery is for. {@code REIMBURSEMENT_RECOVERY} lets the same
 * amortized-recovery mechanism recover an over-paid reimbursement over installments, rather than
 * requiring a second, parallel recovery engine for that case.
 */
public enum LoanType {
    PERSONAL_LOAN,
    SALARY_ADVANCE,
    REIMBURSEMENT_RECOVERY,
    OTHER
}
