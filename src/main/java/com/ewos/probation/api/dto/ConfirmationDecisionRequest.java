package com.ewos.probation.api.dto;

import jakarta.validation.constraints.Size;

public record ConfirmationDecisionRequest(@Size(max = 4000) String notes) {}
