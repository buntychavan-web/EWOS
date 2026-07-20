package com.ewos.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TransitionDefinitionSpec(
        @NotBlank @Size(max = 64) String fromStateCode,
        @NotBlank @Size(max = 64) String toStateCode,
        @NotBlank @Size(max = 64) String actionCode,
        @Size(max = 64) String requiredRole,
        boolean auto,
        @Size(max = 2048) String guardExpression) {}
