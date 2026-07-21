package com.ewos.attendance.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SubmitTimesheetRequest(
        @NotNull UUID workflowDefinitionId, @Size(max = 2048) String notes) {}
