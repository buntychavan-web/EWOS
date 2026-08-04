package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.BulkVariablePaymentBatchStatus;
import java.util.List;
import java.util.UUID;

/**
 * {@code batchId} is null for a preview (nothing was persisted) and set once a batch is committed.
 * {@code status} is null for a preview since a preview is neither committed nor rejected — it is
 * purely informational.
 */
public record BulkVariablePaymentReportResponse(
        UUID batchId,
        UUID tenantId,
        UUID companyId,
        String sourceFilename,
        BulkVariablePaymentBatchStatus status,
        int totalRows,
        int validRows,
        int errorRows,
        List<BulkVariablePaymentRowResult> rows) {}
