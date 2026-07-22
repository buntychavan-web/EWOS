package com.ewos.probation.api.dto;

import jakarta.validation.constraints.Size;

public record ConfirmProbationRequest(
        @Size(max = 1024) String confirmationLetterUri, @Size(max = 4000) String notes) {}
