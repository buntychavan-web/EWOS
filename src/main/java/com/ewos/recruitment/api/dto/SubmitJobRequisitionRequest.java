package com.ewos.recruitment.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitJobRequisitionRequest(@NotNull UUID workflowDefinitionId) {}
