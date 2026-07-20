package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CompensationLineRequest(
        @NotNull UUID payComponentId,
        @DecimalMin("0.0000") BigDecimal amount,
        @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal percentage) {}
