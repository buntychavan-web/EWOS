package com.ewos.offer.api.dto;

import com.ewos.offer.domain.preboarding.PreboardingChecklistStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PreboardingChecklistResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID offerId,
        UUID applicationId,
        UUID candidateId,
        LocalDate joiningDate,
        PreboardingChecklistStatus status,
        BigDecimal completionPercent,
        Instant joiningConfirmedAt,
        UUID joiningConfirmedBy,
        UUID employeeId,
        String notes,
        long versionNo) {}
