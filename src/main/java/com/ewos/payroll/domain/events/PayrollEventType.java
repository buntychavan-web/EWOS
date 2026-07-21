package com.ewos.payroll.domain.events;

/**
 * Discriminator for {@link PayrollEvent}. Every state transition and every material data change on
 * a payroll aggregate emits one of these on topic {@code ewos.payroll.event}.
 */
public enum PayrollEventType {
    COMPONENT_CHANGED,
    PERIOD_OPENED,
    PERIOD_LOCKED,
    PERIOD_CLOSED,
    COMPENSATION_CHANGED,
    RUN_STARTED,
    RUN_COMPLETED,
    RUN_FINALIZED,
    RUN_FROZEN,
    RUN_FAILED,
    PAYSLIP_GENERATED,
    PAYSLIP_FINALIZED,
    ARREAR_QUEUED,
    ARREAR_APPLIED
}
