package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.LoanStatus;
import com.ewos.payroll.domain.LoanType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeLoanResponse(
        UUID id,
        UUID employeeId,
        LoanType loanType,
        BigDecimal principalAmount,
        BigDecimal annualInterestRatePercent,
        int tenureMonths,
        LocalDate disbursedDate,
        LoanStatus status,
        BigDecimal outstandingPrincipal,
        String notes) {}
