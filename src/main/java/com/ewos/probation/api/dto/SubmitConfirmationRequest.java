package com.ewos.probation.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitConfirmationRequest(@NotNull UUID workflowDefinitionId) {}
