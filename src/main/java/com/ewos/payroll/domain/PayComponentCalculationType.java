package com.ewos.payroll.domain;

/**
 * How a {@link PayComponent} resolves to an amount for a given payslip.
 *
 * <ul>
 *   <li>{@code FIXED} — the configured amount is used verbatim.
 *   <li>{@code PERCENT_OF_BASIC} — amount = percentage * basic salary of the payslip.
 * </ul>
 */
public enum PayComponentCalculationType {
    FIXED,
    PERCENT_OF_BASIC
}
