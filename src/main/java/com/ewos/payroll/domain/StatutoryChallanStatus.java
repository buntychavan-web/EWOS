package com.ewos.payroll.domain;

/**
 * Statutory-challan lifecycle:
 *
 * <ul>
 *   <li>{@code DRAFT} — aggregate rolled up from payslip snapshots; not yet filed.
 *   <li>{@code FILED} — filed with the statutory authority; filing_reference required.
 *   <li>{@code PAID} — payment received / acknowledged by the authority; payment_reference
 *       required. Immutable.
 *   <li>{@code CANCELLED} — filing withdrawn.
 * </ul>
 */
public enum StatutoryChallanStatus {
    DRAFT,
    FILED,
    PAID,
    CANCELLED
}
