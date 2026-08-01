package com.ewos.importexport.api.dto;

/**
 * One failed row from a bulk import — the row number as it appeared in the file (1-indexed, header
 * excluded), the raw row content, and why it was rejected.
 */
public record ImportRowError(int rowNumber, String rowData, String errorMessage) {}
