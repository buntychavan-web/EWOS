package com.ewos.interview.api.dto;

import com.ewos.interview.domain.InterviewParticipantAttendance;
import com.ewos.interview.domain.InterviewParticipantRole;
import java.util.UUID;

public record InterviewParticipantResponse(
        UUID id,
        UUID roundId,
        UUID employeeId,
        InterviewParticipantRole role,
        InterviewParticipantAttendance attendance,
        String notes,
        String externalCalendarRef,
        long versionNo) {}
