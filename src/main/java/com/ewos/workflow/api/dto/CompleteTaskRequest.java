package com.ewos.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteTaskRequest(
        @NotBlank @Size(max = 64) String actionCode,
        @Size(max = 64) String outcomeCode,
        @Size(max = 2048) String notes) {}
