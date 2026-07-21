package com.ewos.ats.api.dto;

import com.ewos.ats.domain.NoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddCandidateNoteRequest(
        @NotNull NoteType noteType, @NotBlank @Size(max = 8000) String body, Boolean privateNote) {}
