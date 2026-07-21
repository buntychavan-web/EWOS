package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayComponentCalculationType;
import com.ewos.payroll.domain.PayComponentKind;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePayComponentRequest(
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$") String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotNull PayComponentKind kind,
        @NotNull PayComponentCalculationType calculationType,
        @DecimalMin("0.0000") BigDecimal defaultAmount,
        @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal defaultPercentage,
        Boolean taxable,
        Boolean active,
        @PositiveOrZero Integer sortOrder) {}
