package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReopenPayrollPeriodRequest(@NotBlank @Size(max = 2000) String reason) {}
