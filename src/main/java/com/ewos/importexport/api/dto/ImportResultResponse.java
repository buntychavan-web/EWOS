package com.ewos.importexport.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of a bulk import — the validation/error report a caller reads to see exactly which rows
 * succeeded and why the rest didn't. {@code successCount + failureCount == totalRows} always;
 * partial success (some rows imported, others rejected) is the normal case, not an error state —
 * the overall HTTP response is always 200 as long as the file itself was readable.
 */
public record ImportResultResponse(
        UUID jobId,
        int totalRows,
        int successCount,
        int failureCount,
        List<ImportRowError> errors) {}
