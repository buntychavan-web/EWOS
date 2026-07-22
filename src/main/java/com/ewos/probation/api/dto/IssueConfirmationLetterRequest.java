package com.ewos.probation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IssueConfirmationLetterRequest(@NotBlank @Size(max = 1024) String letterUri) {}
