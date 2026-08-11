package com.ewos.reimbursement.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Each item is evaluated and committed independently — mirrors {@code BulkApprovalActionRequest}:
 * one invalid or unauthorized item never rolls back the others.
 */
public record BulkReimbursementDecisionRequest(
        @NotEmpty @Size(max = 100) @Valid List<BulkReimbursementDecisionItemRequest> items) {}
