package com.ewos.employee.api.dto;

import com.ewos.employee.domain.ApprovalSourceModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 27B — one row in the unified manager approvals inbox ({@code GET
 * /api/v1/manager-self-service/approvals}), aggregated across Leave, Timesheet, Performance,
 * Probation, and Requisition. {@code actionable} tells the client whether {@code POST
 * .../approvals/act} accepts this item, or whether it is a read-only summary card that must be
 * actioned via {@code deepLinkPath} instead.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalItemResponse(
        ApprovalSourceModule sourceModule,
        UUID sourceId,
        boolean actionable,
        UUID subjectEmployeeId,
        String subjectEmployeeName,
        String status,
        String summary,
        Instant pendingSince,
        String deepLinkPath) {}
