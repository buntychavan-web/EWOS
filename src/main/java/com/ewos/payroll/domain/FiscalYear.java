package com.ewos.payroll.domain;

import java.time.LocalDate;

/** Indian fiscal year (1 April&ndash;31 March) helpers shared by the statutory engine. */
public final class FiscalYear {

    private static final int START_MONTH = 4;
    private static final int END_MONTH = 3;

    private FiscalYear() {}

    /** Fiscal-year label ("2025-26") for the year starting 1 April containing {@code date}. */
    public static String labelFor(LocalDate date) {
        int startYear = startYear(date);
        int endYearShort = (startYear + 1) % 100;
        return String.format("%d-%02d", startYear, endYearShort);
    }

    /**
     * Months from {@code date} to 31 March of its fiscal year, inclusive of {@code date}'s month.
     */
    public static int monthsRemaining(LocalDate date) {
        int startYear = startYear(date);
        LocalDate fiscalYearEnd = LocalDate.of(startYear + 1, END_MONTH, 1);
        int months =
                (fiscalYearEnd.getYear() - date.getYear()) * 12
                        + (fiscalYearEnd.getMonthValue() - date.getMonthValue())
                        + 1;
        return Math.max(1, months);
    }

    private static int startYear(LocalDate date) {
        return date.getMonthValue() >= START_MONTH ? date.getYear() : date.getYear() - 1;
    }
}
