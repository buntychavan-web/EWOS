package com.ewos.employee.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Sprint 27B — {@code POST /api/v1/manager-self-service/approvals/bulk-act}. Each {@link
 * BulkApprovalItemRequest} is evaluated and committed independently (PRD §5.4/§17): one invalid or
 * unauthorized item never rolls back the others — see {@code ManagerApprovalsService#bulkAct}.
 */
public record BulkApprovalActionRequest(
        @NotEmpty @Size(max = 100) @Valid List<BulkApprovalItemRequest> items) {}
