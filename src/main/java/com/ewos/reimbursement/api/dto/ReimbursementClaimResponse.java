package com.ewos.reimbursement.api.dto;

import com.ewos.reimbursement.domain.ReimbursementClaimStatus;
import com.ewos.reimbursement.domain.ReimbursementDataSource;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReimbursementClaimResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        String claimNumber,
        UUID categoryId,
        String categoryCode,
        String categoryName,
        BigDecimal amount,
        String currency,
        LocalDate claimDate,
        String description,
        ReimbursementClaimStatus status,
        ReimbursementDataSource dataSource,
        Instant submittedAt,
        Instant managerDecisionAt,
        UUID managerDecisionBy,
        Instant financeDecisionAt,
        UUID financeDecisionBy,
        String rejectionReason,
        UUID workflowInstanceId,
        List<ReimbursementClaimAttachmentResponse> attachments,
        Instant createdAt,
        Instant updatedAt,
        long versionNo) {}
