package com.ewos.payroll.domain;

/**
 * What kind of payslip element a {@link GLMapping} routes to GL accounts.
 *
 * <ul>
 *   <li>{@code PAY_COMPONENT} — matched by {@link PayComponent#getCode()} on payslip lines.
 *   <li>{@code EMPLOYER_CONTRIBUTION} — statutory rows with a non-zero employer contribution.
 *   <li>{@code NET_PAY} — the sink for take-home; {@code source_code = "NET"} by convention.
 *   <li>{@code PROVISION} — end-of-period expense provisions (e.g. bonus provision, gratuity).
 *   <li>{@code ACCRUAL} — expense accruals (e.g. unpaid leave accrual).
 *   <li>{@code STATUTORY} — matched by {@link StatutoryDeduction#getCode()}.
 * </ul>
 */
public enum GLMappingSourceKind {
    PAY_COMPONENT,
    EMPLOYER_CONTRIBUTION,
    NET_PAY,
    PROVISION,
    ACCRUAL,
    STATUTORY
}
