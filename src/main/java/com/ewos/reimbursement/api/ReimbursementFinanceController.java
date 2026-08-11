package com.ewos.reimbursement.api;

import com.ewos.employee.domain.ApprovalAction;
import com.ewos.reimbursement.api.dto.BulkReimbursementDecisionItemRequest;
import com.ewos.reimbursement.api.dto.BulkReimbursementDecisionItemResult;
import com.ewos.reimbursement.api.dto.BulkReimbursementDecisionRequest;
import com.ewos.reimbursement.api.dto.BulkReimbursementDecisionResponse;
import com.ewos.reimbursement.api.dto.DecideReimbursementClaimRequest;
import com.ewos.reimbursement.api.dto.ReimbursementClaimResponse;
import com.ewos.reimbursement.application.ReimbursementClaimService;
import com.ewos.shared.exception.ApiException;
import com.ewos.shared.idempotency.IdempotencyService;
import com.ewos.tenancy.application.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 2 — finance-level reimbursement decisions. Not routed through the unified MSS approvals
 * inbox: finance reviewers are role-based ({@code REIMBURSEMENT_FINANCE_APPROVE}), not a manager
 * chain, mirroring {@code PayrollApprovalController}'s dedicated shape exactly.
 */
@RestController
@RequestMapping("/api/v1/reimbursements")
@Tag(
        name = "Reimbursement Finance",
        description = "Finance-level decisions on manager-approved claims")
public class ReimbursementFinanceController {

    private static final String HEADER = "Idempotency-Key";

    private final ReimbursementClaimService claims;
    private final TenantContext tenantContext;
    private final IdempotencyService idempotency;

    public ReimbursementFinanceController(
            ReimbursementClaimService claims,
            TenantContext tenantContext,
            IdempotencyService idempotency) {
        this.claims = claims;
        this.tenantContext = tenantContext;
        this.idempotency = idempotency;
    }

    @GetMapping("/finance/pending")
    @PreAuthorize("hasAuthority('REIMBURSEMENT_FINANCE_APPROVE')")
    @Operation(summary = "MANAGER_APPROVED claims awaiting a finance decision, for one company")
    public Page<ReimbursementClaimResponse> pending(
            @RequestParam UUID companyId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return claims.pendingForFinance(tenantContext.homeTenantId(), companyId, pageable);
    }

    @PostMapping("/{id}/finance/approve")
    @PreAuthorize("hasAuthority('REIMBURSEMENT_FINANCE_APPROVE')")
    @Operation(
            summary =
                    "Approve a MANAGER_APPROVED claim; the submitter can never approve their own claim")
    public ReimbursementClaimResponse approve(
            @PathVariable UUID id,
            @RequestHeader(value = HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody DecideReimbursementClaimRequest request) {
        requireIdempotencyKey(idempotencyKey);
        UUID tenantId = tenantContext.homeTenantId();
        UUID actor = requireActorId();
        return idempotency.execute(
                tenantId,
                actor,
                "reimbursement.claim.finance-approve",
                idempotencyKey,
                ReimbursementClaimResponse.class,
                () -> claims.financeApprove(tenantId, id, actor, request));
    }

    @PostMapping("/{id}/finance/reject")
    @PreAuthorize("hasAuthority('REIMBURSEMENT_FINANCE_APPROVE')")
    @Operation(summary = "Reject a MANAGER_APPROVED claim; a reason is required")
    public ReimbursementClaimResponse reject(
            @PathVariable UUID id,
            @RequestHeader(value = HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody DecideReimbursementClaimRequest request) {
        requireIdempotencyKey(idempotencyKey);
        UUID tenantId = tenantContext.homeTenantId();
        UUID actor = requireActorId();
        return idempotency.execute(
                tenantId,
                actor,
                "reimbursement.claim.finance-reject",
                idempotencyKey,
                ReimbursementClaimResponse.class,
                () -> claims.financeReject(tenantId, id, actor, request));
    }

    @PostMapping("/finance/bulk-act")
    @PreAuthorize("hasAuthority('REIMBURSEMENT_FINANCE_APPROVE')")
    @Operation(
            summary =
                    "Decide multiple claims in one call; each line is evaluated and committed"
                            + " independently — one failure never rolls back another")
    public BulkReimbursementDecisionResponse bulkAct(
            @RequestHeader(value = HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody BulkReimbursementDecisionRequest request) {
        requireIdempotencyKey(idempotencyKey);
        UUID tenantId = tenantContext.homeTenantId();
        UUID actor = requireActorId();
        return idempotency.execute(
                tenantId,
                actor,
                "reimbursement.claim.finance-bulk-act",
                idempotencyKey,
                BulkReimbursementDecisionResponse.class,
                () -> doBulkAct(tenantId, actor, request));
    }

    private BulkReimbursementDecisionResponse doBulkAct(
            UUID tenantId, UUID actor, BulkReimbursementDecisionRequest request) {
        List<BulkReimbursementDecisionItemResult> results = new ArrayList<>(request.items().size());
        int succeeded = 0;
        for (BulkReimbursementDecisionItemRequest item : request.items()) {
            try {
                DecideReimbursementClaimRequest decide =
                        new DecideReimbursementClaimRequest(item.reason());
                if (item.action() == ApprovalAction.APPROVE) {
                    claims.financeApprove(tenantId, item.claimId(), actor, decide);
                } else {
                    claims.financeReject(tenantId, item.claimId(), actor, decide);
                }
                results.add(BulkReimbursementDecisionItemResult.success(item.claimId()));
                succeeded++;
            } catch (ApiException e) {
                results.add(
                        BulkReimbursementDecisionItemResult.failed(item.claimId(), safeMessage(e)));
            } catch (RuntimeException e) {
                results.add(
                        BulkReimbursementDecisionItemResult.failed(
                                item.claimId(), "Unexpected error"));
            }
        }
        return new BulkReimbursementDecisionResponse(
                results, succeeded, results.size() - succeeded);
    }

    private static String safeMessage(ApiException e) {
        return e.getStatus() == HttpStatus.NOT_FOUND || e.getStatus() == HttpStatus.FORBIDDEN
                ? "Not found or not authorized"
                : e.getMessage();
    }

    private UUID requireActorId() {
        return tenantContext
                .currentUserId()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.UNAUTHORIZED, "Authenticated user required"));
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, HEADER + " header is required");
        }
    }
}
