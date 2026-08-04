package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayComponentKind;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of a bulk variable-payment upload — the same shape a spreadsheet/CSV row would carry.
 * Employees are referenced by {@code employeeNumber} (not an internal UUID) since that is what an
 * uploaded file realistically contains; the service resolves it to the employee record.
 */
public record BulkVariablePaymentRow(
        String employeeNumber,
        String reasonCode,
        String description,
        BigDecimal amount,
        PayComponentKind kind,
        LocalDate forPeriodStart,
        LocalDate forPeriodEnd) {}
