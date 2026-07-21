package com.ewos.ats.api.dto;

import com.ewos.ats.domain.CandidateStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeCandidateStatusRequest(
        @NotNull CandidateStatus status, @Size(max = 2000) String reason) {}
