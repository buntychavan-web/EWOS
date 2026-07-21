package com.ewos.payroll.domain;

/**
 * High-level nature of a general-ledger account. Governs debit/credit direction in journal
 * balancing: increases to ASSET/EXPENSE hit debit, increases to LIABILITY/EQUITY/REVENUE hit
 * credit. SUSPENSE is a catch-all for imbalance handling.
 */
public enum GLAccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE,
    SUSPENSE
}
