package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePfConfigurationRequest(
        @NotNull UUID tenantId,
        UUID companyId,
        @NotNull @DecimalMin("0.01") BigDecimal wageCeiling,
        @NotNull @DecimalMin("0.01") BigDecimal epsWageCeiling,
        @NotNull @DecimalMin("0.0001") BigDecimal employeeRatePct,
        @NotNull @DecimalMin("0.0001") BigDecimal employerPfRatePct,
        @NotNull @DecimalMin("0.0001") BigDecimal epsRatePct,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active) {}
