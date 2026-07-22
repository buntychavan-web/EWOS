package com.ewos.learning.api.dto;

import com.ewos.learning.domain.EnrollmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID courseId,
        UUID sessionId,
        UUID employeeId,
        UUID learningPathId,
        UUID nominatedBy,
        Instant nominatedAt,
        Instant enrolledAt,
        Instant startedAt,
        Instant completedAt,
        Instant withdrawnAt,
        String withdrawalReason,
        BigDecimal attendancePercent,
        BigDecimal assessmentScore,
        Boolean passed,
        EnrollmentStatus status) {}
