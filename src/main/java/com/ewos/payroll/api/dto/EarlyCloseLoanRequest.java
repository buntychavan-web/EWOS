package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EarlyCloseLoanRequest(@NotBlank @Size(max = 2000) String reason) {}
