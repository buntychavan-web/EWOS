package com.ewos.ats.api.dto;

import com.ewos.ats.domain.DuplicateMatchType;
import java.util.UUID;

public record DuplicateCandidateMatchResponse(
        UUID candidateId, String candidateNumber, String fullName, DuplicateMatchType matchType) {}
