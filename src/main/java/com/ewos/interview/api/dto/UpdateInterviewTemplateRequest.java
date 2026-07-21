package com.ewos.interview.api.dto;

import com.ewos.interview.domain.InterviewType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateInterviewTemplateRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        @NotNull InterviewType interviewType,
        @Min(1) @Max(1440) Integer defaultDurationMinutes,
        String scorecardSchema,
        Boolean active) {}
