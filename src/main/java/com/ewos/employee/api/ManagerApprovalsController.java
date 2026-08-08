package com.ewos.employee.api;

import com.ewos.employee.api.dto.ApprovalDecisionRequest;
import com.ewos.employee.api.dto.ApprovalItemResponse;
import com.ewos.employee.api.dto.ApprovalsPageResponse;
import com.ewos.employee.api.dto.BulkApprovalActionRequest;
import com.ewos.employee.api.dto.BulkApprovalActionResponse;
import com.ewos.employee.application.ManagerApprovalsService;
import com.ewos.employee.domain.ApprovalAction;
import com.ewos.employee.domain.ApprovalSourceModule;
import com.ewos.shared.exception.ApiException;
import com.ewos.shared.idempotency.IdempotencyService;
import com.ewos.tenancy.application.TenantContext;
import com.ewos.workflow.api.dto.WorkflowDelegationResponse;
import com.ewos.workflow.application.WorkflowDelegationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 27B — the unified manager approvals inbox. Requires only authentication, mirroring every
 * other self-service controller: results and actions are scoped server-side to the caller's own
 * direct reports (or, with {@code actingForEmployeeId}, a peer who has actively delegated to them
 * via the existing {@code /api/v1/workflow/delegations} mechanism).
 */
@RestController
@RequestMapping("/api/v1/manager-self-service/approvals")
@Tag(
        name = "Manager Self-Service Approvals",
        description =
                "Unified inbox aggregating Leave/Timesheet/Performance/Probation/Requisition"
                        + " approvals pending the caller's decision")
public class ManagerApprovalsController {

    private static final String HEADER = "Idempotency-Key";

    private final ManagerApprovalsService service;
    private final WorkflowDelegationService delegations;
    private final TenantContext tenantContext;
    private final IdempotencyService idempotency;

    public ManagerApprovalsController(
            ManagerApprovalsService service,
            WorkflowDelegationService delegations,
            TenantContext tenantContext,
            IdempotencyService idempotency) {
        this.service = service;
        this.delegations = delegations;
        this.tenantContext = tenantContext;
        this.idempotency = idempotency;
    }

    @GetMapping
    @Operation(
            summary =
                    "Cursor-paginated approvals pending the caller's decision, merged across every"
                            + " supported module")
    public ApprovalsPageResponse list(
            @RequestParam(required = false) UUID actingForEmployeeId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return service.list(actingForEmployeeId, cursor, limit);
    }

    @GetMapping("/delegators")
    @Operation(
            summary =
                    "Peers currently delegating their approvals inbox to the caller, for the"
                            + " \"acting for [X]\" picker")
    public List<WorkflowDelegationResponse> delegators() {
        UUID actorId =
                tenantContext
                        .currentUserId()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.UNAUTHORIZED,
                                                "Authenticated user required"));
        return delegations.activeDelegatorsFor(tenantContext.homeTenantId(), actorId);
    }

    @PostMapping("/{sourceModule}/{sourceId}/approve")
    @Operation(summary = "Approve a Leave or Timesheet item directly from the inbox")
    public ApprovalItemResponse approve(
            @PathVariable ApprovalSourceModule sourceModule,
            @PathVariable UUID sourceId,
            @RequestParam(required = false) UUID actingForEmployeeId,
            @RequestHeader(value = HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
        String notes = request == null ? null : request.notes();
        return idempotent(
                "approvals.approve",
                idempotencyKey,
                ApprovalItemResponse.class,
                () ->
                        service.decide(
                                sourceModule,
                                sourceId,
                                actingForEmployeeId,
                                ApprovalAction.APPROVE,
                                notes));
    }

    @PostMapping("/{sourceModule}/{sourceId}/reject")
    @Operation(summary = "Reject a Leave or Timesheet item directly from the inbox")
    public ApprovalItemResponse reject(
            @PathVariable ApprovalSourceModule sourceModule,
            @PathVariable UUID sourceId,
            @RequestParam(required = false) UUID actingForEmployeeId,
            @RequestHeader(value = HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
        String notes = request == null ? null : request.notes();
        return idempotent(
                "approvals.reject",
                idempotencyKey,
                ApprovalItemResponse.class,
                () ->
                        service.decide(
                                sourceModule,
                                sourceId,
                                actingForEmployeeId,
                                ApprovalAction.REJECT,
                                notes));
    }

    @PostMapping("/bulk-act")
    @Operation(
            summary =
                    "Approve/reject up to 100 items in one call; each item is evaluated and"
                            + " committed independently, so one invalid item never rolls back the"
                            + " others")
    public BulkApprovalActionResponse bulkAct(
            @RequestParam(required = false) UUID actingForEmployeeId,
            @RequestHeader(value = HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody BulkApprovalActionRequest request) {
        return idempotent(
                "approvals.bulk-act",
                idempotencyKey,
                BulkApprovalActionResponse.class,
                () -> service.bulkAct(request, actingForEmployeeId));
    }

    private <T> T idempotent(
            String endpoint,
            String idempotencyKey,
            Class<T> responseType,
            java.util.function.Supplier<T> action) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID actorId =
                tenantContext
                        .currentUserId()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.UNAUTHORIZED,
                                                "Authenticated user required"));
        return idempotency.execute(
                tenantId, actorId, endpoint, idempotencyKey, responseType, action);
    }
}
