package com.ewos.leave.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitLeaveRequestRequest(@NotNull UUID workflowDefinitionId) {}
