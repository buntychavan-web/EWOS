package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateEsiConfigurationRequest(
        @DecimalMin("0.01") BigDecimal wageThreshold,
        @DecimalMin("0.0001") BigDecimal employeeRatePct,
        @DecimalMin("0.0001") BigDecimal employerRatePct,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active) {}
