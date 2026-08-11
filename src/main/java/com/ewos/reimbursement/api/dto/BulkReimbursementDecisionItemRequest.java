package com.ewos.reimbursement.api.dto;

import com.ewos.employee.domain.ApprovalAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** One line of a finance bulk decide request. Reuses {@link ApprovalAction} (APPROVE/REJECT). */
public record BulkReimbursementDecisionItemRequest(
        @NotNull UUID claimId, @NotNull ApprovalAction action, @Size(max = 2000) String reason) {}
