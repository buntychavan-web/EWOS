package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollFrequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EmployeeCompensationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        PayrollFrequency frequency,
        BigDecimal basicSalary,
        String currency,
        String notes,
        boolean active,
        List<CompensationLineResponse> lines,
        long versionNo) {}
