package com.ewos.payroll.domain;

/**
 * Bank advice lifecycle:
 *
 * <ul>
 *   <li>{@code DRAFT} — created; no instructions yet.
 *   <li>{@code GENERATED} — instructions materialised; file ready to hand to the bank.
 *   <li>{@code ACKNOWLEDGED} — bank has confirmed receipt.
 *   <li>{@code SETTLED} — every instruction is either PAID or SKIPPED.
 *   <li>{@code FAILED} — batch rejected by the bank; instructions retain their statuses.
 * </ul>
 */
public enum BankAdviceStatus {
    DRAFT,
    GENERATED,
    ACKNOWLEDGED,
    SETTLED,
    FAILED
}
