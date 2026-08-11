package com.ewos.reimbursement.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Transient suggestion only — never persisted as such. The employee reviews these values and, if
 * they choose to keep them (possibly corrected), submits them through the ordinary {@code
 * CreateReimbursementClaimRequest}/{@code UpdateReimbursementClaimRequest} endpoints, which are the
 * only path that ever writes a claim's real values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReceiptExtractionResponse(
        String merchantName,
        LocalDate billDate,
        BigDecimal totalAmount,
        String currency,
        UUID suggestedCategoryId,
        Map<String, Double> fieldConfidence,
        String providerVersion) {}
