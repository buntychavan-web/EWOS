package com.ewos.payroll.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Creates or replaces the company's maker-checker hierarchy in one call — levels are edited as a
 * whole.
 */
public record SetPayrollApprovalPolicyRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotEmpty @Valid List<PayrollApprovalLevelRequest> levels) {}
