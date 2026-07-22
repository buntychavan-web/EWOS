package com.ewos.exit.api.dto;

import com.ewos.exit.domain.ClearanceStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateClearanceRequest(@NotNull ClearanceStatus status, String notes) {}
