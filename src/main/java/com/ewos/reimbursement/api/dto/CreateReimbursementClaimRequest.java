package com.ewos.reimbursement.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Creates a DRAFT claim. tenantId/companyId/employeeId are resolved server-side from the caller's
 * own identity, never taken from the request body — mirrors {@code SelfLeaveRequestRequest}.
 */
public record CreateReimbursementClaimRequest(
        @NotNull UUID categoryId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        @NotNull @PastOrPresent LocalDate claimDate,
        @NotBlank @Size(max = 2000) String description) {}
