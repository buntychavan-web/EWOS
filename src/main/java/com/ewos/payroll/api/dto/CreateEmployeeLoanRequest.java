package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.LoanType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateEmployeeLoanRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotNull LoanType loanType,
        @NotNull @Positive BigDecimal principalAmount,
        @NotNull @DecimalMin("0.0") BigDecimal annualInterestRatePercent,
        @Min(1) @Max(600) int tenureMonths,
        @NotNull LocalDate disbursedDate,
        @Size(max = 2000) String notes) {}
