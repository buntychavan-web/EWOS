package com.ewos.payroll.api.dto.reports;

import java.math.BigDecimal;
import java.util.UUID;

/** Employee-level variance between two payroll runs. */
public record VarianceRowResponse(
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        BigDecimal current,
        BigDecimal previous,
        BigDecimal delta,
        BigDecimal deltaPercent) {}
