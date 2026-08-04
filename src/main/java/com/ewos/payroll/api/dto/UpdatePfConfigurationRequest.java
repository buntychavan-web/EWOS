package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdatePfConfigurationRequest(
        @DecimalMin("0.01") BigDecimal wageCeiling,
        @DecimalMin("0.01") BigDecimal epsWageCeiling,
        @DecimalMin("0.0001") BigDecimal employeeRatePct,
        @DecimalMin("0.0001") BigDecimal employerPfRatePct,
        @DecimalMin("0.0001") BigDecimal epsRatePct,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active) {}
