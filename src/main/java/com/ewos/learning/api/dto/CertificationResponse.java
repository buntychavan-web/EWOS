package com.ewos.learning.api.dto;

import com.ewos.learning.domain.CertificationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CertificationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID courseId,
        UUID enrollmentId,
        String certificationName,
        String issuingBody,
        String referenceNumber,
        LocalDate issuedAt,
        LocalDate expiresAt,
        CertificationStatus status,
        String certificateUri,
        Instant revokedAt,
        String revocationReason) {}
