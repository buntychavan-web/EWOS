package com.ewos.learning.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record IssueCertificationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        UUID courseId,
        UUID enrollmentId,
        @NotBlank @Size(max = 256) String certificationName,
        @Size(max = 256) String issuingBody,
        @Size(max = 128) String referenceNumber,
        @NotNull LocalDate issuedAt,
        LocalDate expiresAt,
        @Size(max = 1024) String certificateUri) {}
