package com.ewos.payroll.api.dto;

/**
 * One explanation fact about a payroll event — a payslip line, a tax adjustment, an exception — in
 * a shape any consumer (ESS UI, admin UI, or a future AI assistant) can render or reason over
 * without needing to know the underlying domain model. See {@code PayrollInsightProvider}.
 */
public record PayrollInsightResponse(
        String category, String title, String detail, String sourceType) {}
