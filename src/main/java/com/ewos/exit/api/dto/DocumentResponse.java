package com.ewos.exit.api.dto;

import com.ewos.exit.domain.ExitDocumentType;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID tenantId,
        UUID resignationId,
        ExitDocumentType documentType,
        String documentUri,
        Instant issuedAt,
        UUID issuedBy,
        String referenceNumber,
        String notes) {}
