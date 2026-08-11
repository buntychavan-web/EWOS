package com.ewos.reimbursement.api.dto;

import java.util.List;

public record BulkReimbursementDecisionResponse(
        List<BulkReimbursementDecisionItemResult> results, int succeeded, int failed) {}
