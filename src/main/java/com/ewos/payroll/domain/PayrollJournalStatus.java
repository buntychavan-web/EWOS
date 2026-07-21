package com.ewos.payroll.domain;

/**
 * Payroll-journal lifecycle:
 *
 * <ul>
 *   <li>{@code DRAFT} — freshly generated; totals reconcile; editable.
 *   <li>{@code APPROVED} — controller-approved; ready to post.
 *   <li>{@code POSTED} — recorded to the GL.
 *   <li>{@code EXPORTED} — file handed to the ERP downstream (SAP / Oracle / Dynamics / CSV).
 *   <li>{@code REVERSED} — a reversal journal has been generated.
 *   <li>{@code CANCELLED} — abandoned before approval.
 * </ul>
 */
public enum PayrollJournalStatus {
    DRAFT,
    APPROVED,
    POSTED,
    EXPORTED,
    REVERSED,
    CANCELLED
}
