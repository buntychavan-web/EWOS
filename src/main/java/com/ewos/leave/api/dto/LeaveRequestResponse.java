package com.ewos.leave.api.dto;

import com.ewos.leave.domain.LeaveRequestStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaveRequestResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID leaveTypeId,
        String leaveTypeCode,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal daysRequested,
        String reason,
        LeaveRequestStatus status,
        Instant submittedAt,
        Instant approvedAt,
        UUID approvedBy,
        Instant rejectedAt,
        UUID rejectedBy,
        String rejectionReason,
        Instant cancelledAt,
        UUID cancelledBy,
        UUID workflowInstanceId,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
