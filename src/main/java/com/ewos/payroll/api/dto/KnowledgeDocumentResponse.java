package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.KnowledgeDocumentStatus;
import com.ewos.payroll.domain.KnowledgeSourceType;
import java.time.LocalDate;
import java.util.UUID;

public record KnowledgeDocumentResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID documentFamilyId,
        int versionNumber,
        KnowledgeSourceType sourceType,
        String title,
        String referenceNumber,
        String summary,
        String tags,
        String storageUri,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        KnowledgeDocumentStatus status) {}
