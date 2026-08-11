package com.ewos.reimbursement.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Partial update of a DRAFT claim — every field optional, an omitted field leaves it unchanged. */
public record UpdateReimbursementClaimRequest(
        UUID categoryId,
        @DecimalMin("0.01") BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        @PastOrPresent LocalDate claimDate,
        @Size(max = 2000) String description) {}
