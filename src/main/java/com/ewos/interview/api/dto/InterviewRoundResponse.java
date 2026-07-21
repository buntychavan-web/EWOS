package com.ewos.interview.api.dto;

import com.ewos.interview.domain.InterviewDecision;
import com.ewos.interview.domain.InterviewMode;
import com.ewos.interview.domain.InterviewStatus;
import com.ewos.interview.domain.InterviewType;
import java.time.Instant;
import java.util.UUID;

public record InterviewRoundResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID applicationId,
        UUID templateId,
        int roundNumber,
        String name,
        InterviewType interviewType,
        int durationMinutes,
        InterviewMode mode,
        String location,
        String meetingUrl,
        Instant scheduledStart,
        Instant scheduledEnd,
        Instant actualStart,
        Instant actualEnd,
        InterviewStatus status,
        InterviewDecision decision,
        String decisionNotes,
        Instant decidedAt,
        UUID decidedBy,
        UUID coordinatorEmployeeId,
        String externalCalendarRef,
        long versionNo) {}
