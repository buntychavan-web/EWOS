package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollFrequency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateEmployeeCompensationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        UUID payGroupId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotNull PayrollFrequency frequency,
        @NotNull @DecimalMin("0.0000") BigDecimal basicSalary,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Size(max = 1024) String notes,
        @Valid List<CompensationLineRequest> lines) {}
