package com.ewos.exit.api.dto;

import com.ewos.exit.domain.ExitDocumentType;
import java.util.UUID;

public record ExitDocumentTemplateResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID orgUnitId,
        ExitDocumentType documentType,
        String title,
        String bodyTemplate,
        boolean active) {}
