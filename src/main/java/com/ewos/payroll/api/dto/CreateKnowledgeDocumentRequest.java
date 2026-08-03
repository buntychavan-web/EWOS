package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.KnowledgeSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateKnowledgeDocumentRequest(
        @NotNull UUID tenantId,
        UUID companyId,
        @NotNull KnowledgeSourceType sourceType,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 255) String referenceNumber,
        @Size(max = 4000) String summary,
        @Size(max = 1000) String tags,
        @Size(max = 2000) String storageUri,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
