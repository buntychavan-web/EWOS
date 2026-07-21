package com.ewos.offer.api.dto;

import com.ewos.offer.domain.preboarding.PreboardingTaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTaskStatusRequest(
        @NotNull PreboardingTaskStatus status, @Size(max = 4000) String notes, String resultJson) {}
