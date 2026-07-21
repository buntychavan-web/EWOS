package com.ewos.interview.api.dto;

import com.ewos.interview.domain.InterviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordInterviewDecisionRequest(
        @NotNull InterviewDecision decision, @Size(max = 4000) String notes) {}
