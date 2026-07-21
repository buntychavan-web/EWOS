package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateEmployeeCostAllocationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        UUID costCentreId,
        UUID businessUnitId,
        UUID departmentOrgUnitId,
        @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal percentage,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
