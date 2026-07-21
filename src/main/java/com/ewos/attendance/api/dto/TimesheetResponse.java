package com.ewos.attendance.api.dto;

import com.ewos.attendance.domain.TimesheetStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimesheetResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID policyId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal workedHours,
        BigDecimal overtimeHours,
        BigDecimal breakHours,
        BigDecimal absenceHours,
        TimesheetStatus status,
        Instant submittedAt,
        Instant approvedAt,
        UUID approvedBy,
        Instant rejectedAt,
        UUID rejectedBy,
        String rejectionReason,
        UUID workflowInstanceId,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
