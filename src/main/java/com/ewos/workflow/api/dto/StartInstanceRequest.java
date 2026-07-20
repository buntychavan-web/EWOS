package com.ewos.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StartInstanceRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID definitionId,
        @NotBlank @Size(max = 128) String subjectType,
        @NotNull UUID subjectId,
        @Size(max = 256) String correlationKey) {}
