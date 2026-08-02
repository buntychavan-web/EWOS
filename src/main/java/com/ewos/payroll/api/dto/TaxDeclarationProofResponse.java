package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.TaxProofType;
import java.time.Instant;
import java.util.UUID;

public record TaxDeclarationProofResponse(
        UUID id,
        UUID employeeTaxDeclarationId,
        TaxProofType proofType,
        String filename,
        String mimeType,
        long sizeBytes,
        String storageUri,
        String notes,
        Instant uploadedAt) {}
