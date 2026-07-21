package com.ewos.interview.api.dto;

import com.ewos.interview.domain.InterviewParticipantAttendance;
import jakarta.validation.constraints.NotNull;

public record UpdateAttendanceRequest(@NotNull InterviewParticipantAttendance attendance) {}
