package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PayChallanRequest(@NotBlank @Size(max = 128) String paymentReference) {}
