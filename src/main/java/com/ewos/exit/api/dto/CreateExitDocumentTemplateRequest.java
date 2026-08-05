package com.ewos.exit.api.dto;

import com.ewos.exit.domain.ExitDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateExitDocumentTemplateRequest(
        @NotNull UUID companyId,
        UUID orgUnitId,
        @NotNull ExitDocumentType documentType,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 8000) String bodyTemplate) {}
