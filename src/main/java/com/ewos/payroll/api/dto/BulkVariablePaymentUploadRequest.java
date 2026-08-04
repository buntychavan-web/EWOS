package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * {@code rows} deliberately has no {@code @Valid} cascade: a malformed individual row must show up
 * as a per-row error in {@code BulkVariablePaymentReportResponse}, not reject the whole HTTP
 * request outright. {@code BulkVariablePaymentService} validates each row itself (reusing {@code
 * CreateArrearRequest}'s bean validation) so both entry points enforce identical rules.
 */
public record BulkVariablePaymentUploadRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @Size(max = 255) String sourceFilename,
        @NotEmpty List<BulkVariablePaymentRow> rows) {}
