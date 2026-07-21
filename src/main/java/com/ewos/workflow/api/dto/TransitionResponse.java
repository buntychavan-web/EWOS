package com.ewos.workflow.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransitionResponse(
        UUID id,
        UUID fromStateId,
        String fromStateCode,
        UUID toStateId,
        String toStateCode,
        String actionCode,
        String requiredRole,
        boolean auto,
        String guardExpression) {}
