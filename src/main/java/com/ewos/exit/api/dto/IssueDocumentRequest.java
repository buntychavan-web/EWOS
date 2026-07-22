package com.ewos.exit.api.dto;

import com.ewos.exit.domain.ExitDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IssueDocumentRequest(
        @NotNull ExitDocumentType documentType,
        @NotBlank String documentUri,
        String referenceNumber,
        String notes) {}
