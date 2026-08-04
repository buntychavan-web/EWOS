package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimLtaJourneyRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotBlank @Size(max = 16) String fiscalYear,
        @NotNull LocalDate claimDate,
        @NotNull @DecimalMin("0.01") BigDecimal amountClaimed,
        @Size(max = 2000) String notes) {}
