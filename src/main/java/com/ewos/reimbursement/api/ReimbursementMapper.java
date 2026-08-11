package com.ewos.reimbursement.api;

import com.ewos.reimbursement.api.dto.ReimbursementCategoryResponse;
import com.ewos.reimbursement.api.dto.ReimbursementClaimAttachmentResponse;
import com.ewos.reimbursement.api.dto.ReimbursementClaimResponse;
import com.ewos.reimbursement.domain.ReimbursementCategory;
import com.ewos.reimbursement.domain.ReimbursementClaim;
import com.ewos.reimbursement.domain.ReimbursementClaimAttachment;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class ReimbursementMapper {

    public ReimbursementClaimResponse toResponse(
            ReimbursementClaim c, List<ReimbursementClaimAttachmentResponse> attachments) {
        return new ReimbursementClaimResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getEmployee() != null ? c.getEmployee().getId() : null,
                c.getClaimNumber(),
                c.getCategory() != null ? c.getCategory().getId() : null,
                c.getCategory() != null ? c.getCategory().getCode() : null,
                c.getCategory() != null ? c.getCategory().getName() : null,
                c.getAmount(),
                c.getCurrency(),
                c.getClaimDate(),
                c.getDescription(),
                c.getStatus(),
                c.getDataSource(),
                c.getSubmittedAt(),
                c.getManagerDecisionAt(),
                c.getManagerDecisionBy(),
                c.getFinanceDecisionAt(),
                c.getFinanceDecisionBy(),
                c.getRejectionReason(),
                c.getWorkflowInstanceId(),
                attachments,
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getVersionNo());
    }

    public ReimbursementClaimAttachmentResponse toResponse(ReimbursementClaimAttachment a) {
        return new ReimbursementClaimAttachmentResponse(
                a.getId(),
                a.getFilename(),
                a.getMimeType(),
                a.getSizeBytes(),
                a.getStorageUri(),
                a.getNotes(),
                a.isOcrAssisted(),
                a.getUploadedAt());
    }

    public ReimbursementCategoryResponse toResponse(ReimbursementCategory c) {
        return new ReimbursementCategoryResponse(
                c.getId(), c.getCode(), c.getName(), c.getDescription());
    }
}
