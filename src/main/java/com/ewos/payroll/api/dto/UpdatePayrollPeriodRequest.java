package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollFrequency;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdatePayrollPeriodRequest(
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$") @Size(max = 64) String code,
        @Size(max = 128) String name,
        PayrollFrequency frequency,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate payDate) {}
