package com.ewos.ats.api.dto;

import com.ewos.ats.domain.DocumentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UploadCandidateDocumentRequest(
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 512) String filename,
        @NotBlank @Size(max = 128) String mimeType,
        @Min(1) long sizeBytes,
        @NotBlank @Size(max = 1024) String storageUri,
        @Size(max = 2000) String notes) {}
