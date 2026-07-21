package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkPaymentFailedRequest(@NotBlank @Size(max = 2048) String failureReason) {}
