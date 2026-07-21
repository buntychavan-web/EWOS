package com.ewos.offer.api.dto;

import com.ewos.offer.domain.EmploymentType;
import com.ewos.offer.domain.OfferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OfferResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String offerNumber,
        UUID applicationId,
        UUID candidateId,
        UUID jobRequisitionId,
        UUID templateId,
        int version,
        UUID previousOfferId,
        String designation,
        UUID departmentOrgUnitId,
        String location,
        EmploymentType employmentType,
        LocalDate targetJoiningDate,
        String currency,
        BigDecimal baseSalary,
        BigDecimal variablePay,
        BigDecimal oneTimeBonus,
        BigDecimal hiringBonus,
        BigDecimal retentionBonus,
        BigDecimal totalCtc,
        Integer noticePeriodDays,
        Integer probationDays,
        String offerDocumentUri,
        OfferStatus status,
        UUID approvalWorkflowInstanceId,
        Instant submittedAt,
        Instant approvedAt,
        Instant extendedAt,
        Instant expiresAt,
        Instant acceptedAt,
        Instant declinedAt,
        String declineReason,
        Instant revisedAt,
        Instant withdrawnAt,
        String withdrawnReason,
        long versionNo) {}
