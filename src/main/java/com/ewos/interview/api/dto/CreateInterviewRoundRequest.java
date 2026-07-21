package com.ewos.interview.api.dto;

import com.ewos.interview.domain.InterviewMode;
import com.ewos.interview.domain.InterviewType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateInterviewRoundRequest(
        @NotNull UUID applicationId,
        UUID templateId,
        @NotBlank @Size(max = 256) String name,
        @NotNull InterviewType interviewType,
        @Min(1) @Max(1440) Integer durationMinutes,
        @NotNull InterviewMode mode,
        @Size(max = 512) String location,
        @Size(max = 1024) String meetingUrl,
        UUID coordinatorEmployeeId) {}
