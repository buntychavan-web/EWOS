package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.DecidePayrollApprovalRequest;
import com.ewos.payroll.api.dto.PayrollApprovalDecisionResponse;
import com.ewos.payroll.api.dto.PayrollApprovalPolicyResponse;
import com.ewos.payroll.api.dto.PayrollApprovalRequestResponse;
import com.ewos.payroll.api.dto.SetPayrollApprovalPolicyRequest;
import com.ewos.payroll.application.PayrollApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 24L item 1 — Payroll Maker-Checker: company policy configuration and per-run decisions.
 */
@RestController
@Tag(name = "Payroll Approvals", description = "Maker-checker approval hierarchy for payroll runs")
public class PayrollApprovalController {

    private final PayrollApprovalService service;

    public PayrollApprovalController(PayrollApprovalService service) {
        this.service = service;
    }

    @PutMapping("/api/v1/payroll/approval-policies")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Create or replace a company's maker-checker approval hierarchy")
    public PayrollApprovalPolicyResponse setPolicy(
            @Valid @RequestBody SetPayrollApprovalPolicyRequest request) {
        return service.setPolicy(request);
    }

    @GetMapping("/api/v1/payroll/approval-policies/{companyId}")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "The company's active approval hierarchy, if any")
    public PayrollApprovalPolicyResponse getPolicy(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID companyId) {
        return service.getPolicy(tenantId, companyId);
    }

    @DeleteMapping("/api/v1/payroll/approval-policies/{companyId}")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Deactivate the company's approval hierarchy (reverts to direct finalize)")
    public PayrollApprovalPolicyResponse deactivatePolicy(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID companyId) {
        return service.deactivatePolicy(tenantId, companyId);
    }

    @PostMapping("/api/v1/payroll/runs/{id}/approvals/decide")
    @PreAuthorize("hasAuthority('PAYROLL_APPROVE')")
    @Operation(
            summary =
                    "Approve or reject the run's current approval level — the preparer may never"
                            + " decide their own run")
    public PayrollApprovalRequestResponse decide(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody DecidePayrollApprovalRequest request) {
        return service.decide(tenantId, id, request);
    }

    @GetMapping("/api/v1/payroll/runs/{id}/approvals")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "This run's approval request status")
    public PayrollApprovalRequestResponse getForRun(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getForRun(tenantId, id);
    }

    @GetMapping("/api/v1/payroll/runs/{id}/approvals/history")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Complete decision history (audit trail) for this run's approval")
    public List<PayrollApprovalDecisionResponse> historyForRun(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.historyForRun(tenantId, id);
    }

    @GetMapping("/api/v1/payroll/approvals/pending/{companyId}")
    @PreAuthorize("hasAuthority('PAYROLL_APPROVE')")
    @Operation(summary = "Every approval request awaiting a decision for this company")
    public List<PayrollApprovalRequestResponse> pendingForCompany(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID companyId) {
        return service.pendingForCompany(tenantId, companyId);
    }
}
