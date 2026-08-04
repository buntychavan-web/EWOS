package com.ewos.payroll.domain;

/**
 * How a {@link PayComponent} resolves to an amount for a given payslip.
 *
 * <ul>
 *   <li>{@code FIXED} — the configured amount is used verbatim.
 *   <li>{@code PERCENT_OF_BASIC} — amount = percentage * basic salary of the payslip.
 *   <li>{@code STATUTORY_PF} — employee Provident Fund + VPF, from {@link
 *       com.ewos.payroll.application.PfCalculationService}.
 *   <li>{@code STATUTORY_ESI} — employee ESI, from {@link
 *       com.ewos.payroll.application.EsiCalculationService}.
 *   <li>{@code STATUTORY_PT} — Professional Tax, from {@link
 *       com.ewos.payroll.application.ProfessionalTaxCalculationService}.
 *   <li>{@code STATUTORY_LWF} — employee Labour Welfare Fund, from {@link
 *       com.ewos.payroll.application.LwfCalculationService}.
 *   <li>{@code STATUTORY_TDS} — monthly income-tax recovery, from {@link
 *       com.ewos.payroll.application.IncomeTaxCalculationService}.
 * </ul>
 *
 * The {@code STATUTORY_*} amounts are never read from the {@code EmployeeCompensationLine} the way
 * {@code FIXED}/{@code PERCENT_OF_BASIC} are — {@link PayrollCalculator} resolves them from a
 * caller-supplied {@link PayrollCalculator.StatutoryAmounts} snapshot instead, so the underlying
 * statutory rule is never hardcoded in the calculator itself.
 */
public enum PayComponentCalculationType {
    FIXED,
    PERCENT_OF_BASIC,
    STATUTORY_PF,
    STATUTORY_ESI,
    STATUTORY_PT,
    STATUTORY_LWF,
    STATUTORY_TDS
}
