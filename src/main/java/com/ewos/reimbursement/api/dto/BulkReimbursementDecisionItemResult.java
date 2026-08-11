package com.ewos.reimbursement.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BulkReimbursementDecisionItemResult(UUID claimId, String status, String error) {

    public static BulkReimbursementDecisionItemResult success(UUID claimId) {
        return new BulkReimbursementDecisionItemResult(claimId, "SUCCESS", null);
    }

    public static BulkReimbursementDecisionItemResult failed(UUID claimId, String error) {
        return new BulkReimbursementDecisionItemResult(claimId, "FAILED", error);
    }
}
