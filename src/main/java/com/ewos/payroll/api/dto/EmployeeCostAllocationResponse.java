package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeCostAllocationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID costCentreId,
        String costCentreCode,
        UUID businessUnitId,
        String businessUnitCode,
        UUID departmentOrgUnitId,
        String departmentCode,
        BigDecimal percentage,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        long versionNo) {}
