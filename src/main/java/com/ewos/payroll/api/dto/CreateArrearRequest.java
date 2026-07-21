package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayComponentKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateArrearRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z0-9_-]+$") String reasonCode,
        @Size(max = 1024) String description,
        @NotNull @DecimalMin("0.0000") BigDecimal amount,
        @NotNull PayComponentKind kind,
        LocalDate forPeriodStart,
        LocalDate forPeriodEnd) {}
