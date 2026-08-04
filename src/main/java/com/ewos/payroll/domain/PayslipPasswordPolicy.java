package com.ewos.payroll.domain;

/**
 * How a company wants its payslip PDFs password-protected, if at all. Deliberately not a single
 * hardcoded convention — password derivation is a company policy decision, not a technical fact, so
 * it is configurable per company instead of assumed.
 */
public enum PayslipPasswordPolicy {
    /** No password protection. */
    NONE,
    /** The employee's employee number, as-is. */
    EMPLOYEE_NUMBER,
    /** The employee's date of birth in DDMMYYYY form (a common, well-established convention). */
    DATE_OF_BIRTH_DDMMYYYY
}
