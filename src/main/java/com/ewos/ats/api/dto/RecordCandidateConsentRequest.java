package com.ewos.ats.api.dto;

import com.ewos.ats.domain.CandidateConsentSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Records a candidate's consent decision (given or withdrawn) and, optionally, the retention basis
 * that follows from it. {@code consentSource} is required when {@code consentGiven} is {@code true}
 * — the service rejects a consent grant with no recorded source.
 */
public record RecordCandidateConsentRequest(
        @NotNull Boolean consentGiven,
        CandidateConsentSource consentSource,
        @Size(max = 64) String retentionPolicyCode,
        Instant retentionExpiresAt) {}
