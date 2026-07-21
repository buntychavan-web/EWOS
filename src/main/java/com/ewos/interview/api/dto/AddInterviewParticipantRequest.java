package com.ewos.interview.api.dto;

import com.ewos.interview.domain.InterviewParticipantRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AddInterviewParticipantRequest(
        @NotNull UUID employeeId, InterviewParticipantRole role, @Size(max = 2000) String notes) {}
