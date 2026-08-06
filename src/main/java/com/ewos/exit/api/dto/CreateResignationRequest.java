package com.ewos.exit.api.dto;

import com.ewos.exit.domain.ResignationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code tenantId} is deliberately not a field here — the HR/manager-facing endpoint sources it
 * from the validated {@code X-Tenant-Id} header, matching every other mutating endpoint in the
 * codebase; a client-supplied tenant id in the body would bypass {@code
 * TenantHeaderValidationFilter} entirely (see {@code WorkflowDefinitionController}'s equivalent
 * fix). {@code resignationType} must not be {@link ResignationType#SELF_RESIGNATION} — that value
 * is reserved for {@code ExitSelfService}, which resolves the caller's own employee id server-side
 * and never accepts this DTO directly.
 */
public record CreateResignationRequest(
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotNull ResignationType resignationType,
        LocalDate intendedLastDay,
        String reason,
        @PositiveOrZero int noticePeriodDays) {}
