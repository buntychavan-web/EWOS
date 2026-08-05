package com.ewos.exit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WaiveNoticeRequest(@NotBlank @Size(max = 2000) String reason) {}
