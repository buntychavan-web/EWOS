package com.ewos.ats.api.dto;

import java.util.UUID;

public record CandidateTagResponse(UUID id, UUID candidateId, String tag) {}
