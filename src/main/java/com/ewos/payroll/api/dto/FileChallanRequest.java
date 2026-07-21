package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FileChallanRequest(@NotBlank @Size(max = 128) String filingReference) {}
