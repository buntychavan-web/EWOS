package com.ewos.ats.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawApplicationRequest(@NotBlank @Size(max = 4000) String notes) {}
