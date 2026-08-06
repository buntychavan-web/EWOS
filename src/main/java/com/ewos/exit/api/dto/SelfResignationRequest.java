package com.ewos.exit.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/**
 * Self-service resignation submission — no {@code tenantId}, {@code companyId}, {@code employeeId},
 * or {@code resignationType} field: {@code ExitSelfService} resolves the caller's own
 * tenant/employee/company server-side and always sets {@code resignationType =
 * ResignationType.SELF_RESIGNATION}, mirroring {@code SelfLeaveRequestRequest}.
 */
public record SelfResignationRequest(
        LocalDate intendedLastDay, String reason, @PositiveOrZero int noticePeriodDays) {}
