package com.ewos.probation.api;

import com.ewos.probation.api.dto.ConfirmProbationRequest;
import com.ewos.probation.api.dto.ConfirmationDecisionRequest;
import com.ewos.probation.api.dto.ExtendProbationRequest;
import com.ewos.probation.api.dto.IssueConfirmationLetterRequest;
import com.ewos.probation.api.dto.OpenProbationRequest;
import com.ewos.probation.api.dto.ProbationDashboardResponse;
import com.ewos.probation.api.dto.ProbationRecordResponse;
import com.ewos.probation.api.dto.ProbationReportRowResponse;
import com.ewos.probation.api.dto.RecordHrRecommendationRequest;
import com.ewos.probation.api.dto.RecordManagerReviewRequest;
import com.ewos.probation.api.dto.SubmitConfirmationRequest;
import com.ewos.probation.api.dto.TerminateProbationRequest;
import com.ewos.probation.application.ProbationService;
import com.ewos.probation.domain.ProbationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/probation")
@Tag(name = "Probation & Confirmation", description = "Per-employee probation lifecycle")
public class ProbationController {

    private final ProbationService probation;

    public ProbationController(ProbationService probation) {
        this.probation = probation;
    }

    @PostMapping("/records")
    @PreAuthorize("hasAuthority('PROBATION_WRITE')")
    @Operation(summary = "Open a probation record for an employee")
    public ResponseEntity<ProbationRecordResponse> open(
            @Valid @RequestBody OpenProbationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(probation.open(req));
    }

    @PostMapping("/records/{id}/extend")
    @PreAuthorize("hasAuthority('PROBATION_WRITE')")
    @Operation(summary = "Extend an existing probation record")
    public ProbationRecordResponse extend(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ExtendProbationRequest req) {
        return probation.extend(tenantId, id, req);
    }

    @PostMapping("/records/{id}/manager-review")
    @PreAuthorize("hasAuthority('PROBATION_RECOMMEND')")
    @Operation(summary = "Record the manager's probation review")
    public ProbationRecordResponse recordManagerReview(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody RecordManagerReviewRequest req) {
        return probation.recordManagerReview(tenantId, id, req);
    }

    @PostMapping("/records/{id}/hr-recommendation")
    @PreAuthorize("hasAuthority('PROBATION_RECOMMEND')")
    @Operation(summary = "Record the HR recommendation (CONFIRM/EXTEND/TERMINATE)")
    public ProbationRecordResponse recordHrRecommendation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody RecordHrRecommendationRequest req) {
        return probation.recordHrRecommendation(tenantId, id, req);
    }

    @PostMapping("/records/{id}/submit-confirmation")
    @PreAuthorize("hasAuthority('PROBATION_WRITE')")
    @Operation(summary = "Submit the confirmation to the approval workflow")
    public ProbationRecordResponse submitConfirmation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody SubmitConfirmationRequest req) {
        return probation.submitConfirmation(tenantId, id, req);
    }

    @PostMapping("/records/{id}/approve")
    @PreAuthorize("hasAuthority('PROBATION_APPROVE')")
    @Operation(summary = "Approve a pending confirmation")
    public ProbationRecordResponse approveConfirmation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmationDecisionRequest req) {
        return probation.approveConfirmation(tenantId, id, req);
    }

    @PostMapping("/records/{id}/reject")
    @PreAuthorize("hasAuthority('PROBATION_APPROVE')")
    @Operation(summary = "Reject a pending confirmation (returns to IN_PROBATION)")
    public ProbationRecordResponse rejectConfirmation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmationDecisionRequest req) {
        return probation.rejectConfirmation(tenantId, id, req);
    }

    @PostMapping("/records/{id}/confirm")
    @PreAuthorize("hasAuthority('PROBATION_APPROVE')")
    @Operation(summary = "Confirm the probation (final outcome)")
    public ProbationRecordResponse confirm(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid ConfirmProbationRequest req) {
        return probation.confirm(tenantId, id, req);
    }

    @PostMapping("/records/{id}/confirmation-letter")
    @PreAuthorize("hasAuthority('PROBATION_WRITE')")
    @Operation(summary = "Attach or replace the confirmation-letter URI on a confirmed record")
    public ProbationRecordResponse issueConfirmationLetter(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody IssueConfirmationLetterRequest req) {
        return probation.issueConfirmationLetter(tenantId, id, req);
    }

    @PostMapping("/records/{id}/terminate")
    @PreAuthorize("hasAuthority('PROBATION_APPROVE')")
    @Operation(summary = "Terminate the probation (final outcome)")
    public ProbationRecordResponse terminate(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody TerminateProbationRequest req) {
        return probation.terminate(tenantId, id, req);
    }

    @PostMapping("/records/{id}/cancel")
    @PreAuthorize("hasAuthority('PROBATION_WRITE')")
    @Operation(summary = "Cancel a probation record (e.g., resignation before period end)")
    public ProbationRecordResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        return probation.cancel(tenantId, id, reason);
    }

    @GetMapping("/records/{id}")
    @PreAuthorize("hasAuthority('PROBATION_READ')")
    @Operation(summary = "Fetch a probation record by id")
    public ProbationRecordResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return probation.getById(tenantId, id);
    }

    @GetMapping("/records/by-employee/{employeeId}")
    @PreAuthorize("hasAuthority('PROBATION_READ')")
    @Operation(summary = "Fetch a probation record by employee id")
    public ProbationRecordResponse getByEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return probation.getByEmployee(tenantId, employeeId);
    }

    @GetMapping("/records/by-status")
    @PreAuthorize("hasAuthority('PROBATION_READ')")
    @Operation(summary = "List probation records for a company filtered by status")
    public List<ProbationRecordResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam ProbationStatus status) {
        return probation.byStatus(tenantId, companyId, status);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('PROBATION_READ')")
    @Operation(summary = "Aggregate probation counts by status for a company")
    public ProbationDashboardResponse dashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return probation.dashboard(tenantId, companyId);
    }

    @GetMapping("/reports/by-status")
    @PreAuthorize("hasAuthority('PROBATION_READ')")
    @Operation(summary = "Per-employee probation report filtered by status")
    public List<ProbationReportRowResponse> reportByStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam ProbationStatus status) {
        return probation.report(tenantId, companyId, status);
    }

    @GetMapping("/reports/due")
    @PreAuthorize("hasAuthority('PROBATION_READ')")
    @Operation(summary = "Probation records whose effective end falls on or before the given date")
    public List<ProbationReportRowResponse> dueThrough(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through) {
        return probation.dueThrough(tenantId, companyId, through);
    }
}
