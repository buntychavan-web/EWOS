package com.ewos.payroll.domain;

/**
 * Output format for a {@link BankAdvice} file. CSV is the universal fallback; NACHA (US), NEFT
 * (IN), and SEPA (EU) reserve slots for future format-specific writers.
 */
public enum BankAdviceFormat {
    CSV,
    NACHA,
    NEFT,
    SEPA
}
