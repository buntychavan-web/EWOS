package com.ewos.employee.api.dto;

import java.util.List;

/** Sprint 27B — one result per input line, same order as the request (PRD §17 partial-success). */
public record BulkApprovalActionResponse(
        List<BulkApprovalItemResult> results, int succeeded, int failed) {}
