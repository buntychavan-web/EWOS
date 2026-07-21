package com.ewos.ats.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCandidateTagRequest(@NotBlank @Size(max = 128) String tag) {}
