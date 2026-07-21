package com.ewos.recruitment.api.dto;

import com.ewos.recruitment.domain.EmploymentType;
import com.ewos.recruitment.domain.RequisitionPriority;
import com.ewos.recruitment.domain.RequisitionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JobRequisitionResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String requisitionNumber,
        UUID jobPositionId,
        String title,
        UUID departmentOrgUnitId,
        String location,
        EmploymentType employmentType,
        int headcount,
        int filledCount,
        RequisitionPriority priority,
        String justification,
        UUID hiringManagerId,
        UUID recruiterId,
        LocalDate targetStartDate,
        String budgetCurrency,
        BigDecimal budgetAmount,
        RequisitionStatus status,
        UUID workflowInstanceId,
        Instant submittedAt,
        Instant decidedAt,
        UUID decidedBy,
        String decisionNotes,
        Instant openedAt,
        Instant closedAt,
        String closedReason,
        long versionNo) {}
