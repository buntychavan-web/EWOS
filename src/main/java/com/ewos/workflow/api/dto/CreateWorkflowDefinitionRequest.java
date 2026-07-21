package com.ewos.workflow.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateWorkflowDefinitionRequest(
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$") String code,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2048) String description,
        @NotBlank @Size(max = 128) String subjectType,
        @Positive Integer definitionVersion,
        @NotEmpty @Valid List<StateDefinitionSpec> states,
        @Valid List<TransitionDefinitionSpec> transitions) {}
