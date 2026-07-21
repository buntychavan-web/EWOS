package com.ewos.ats.api.dto;

import com.ewos.ats.domain.NoteType;
import java.time.Instant;
import java.util.UUID;

public record CandidateNoteResponse(
        UUID id,
        UUID candidateId,
        NoteType noteType,
        String body,
        boolean privateNote,
        UUID createdBy,
        Instant createdAt,
        long versionNo) {}
