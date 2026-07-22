package com.ewos.performance.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitAppraisalForApprovalRequest(@NotNull UUID workflowDefinitionId) {}
