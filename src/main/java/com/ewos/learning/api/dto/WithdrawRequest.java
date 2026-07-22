package com.ewos.learning.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawRequest(@NotBlank @Size(max = 2000) String reason) {}
