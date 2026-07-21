package com.ewos.ats.domain;

import java.util.UUID;

/** Signal describing a potentially-duplicate candidate hit. */
public record DuplicateCandidateMatch(
        UUID candidateId, String candidateNumber, String fullName, DuplicateMatchType matchType) {}
