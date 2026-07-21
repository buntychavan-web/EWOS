package com.ewos.ats.api.dto;

import java.util.List;

/**
 * Envelope returned by {@code POST /candidates} that also surfaces any potential-duplicate hits.
 * The candidate is always created — clients can present the dupe list and let a workflow decide.
 */
public record CreateCandidateResult(
        CandidateResponse candidate, List<DuplicateCandidateMatchResponse> potentialDuplicates) {}
