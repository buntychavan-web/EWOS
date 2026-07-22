package com.ewos.performance.api;

import com.ewos.performance.api.dto.AppraisalDecisionRequest;
import com.ewos.performance.api.dto.AppraisalReportRowResponse;
import com.ewos.performance.api.dto.AppraisalResponse;
import com.ewos.performance.api.dto.BellCurveBucketResponse;
import com.ewos.performance.api.dto.CalibrationRequest;
import com.ewos.performance.api.dto.IncrementRecommendationRequest;
import com.ewos.performance.api.dto.ManagerAssessmentRequest;
import com.ewos.performance.api.dto.OpenAppraisalRequest;
import com.ewos.performance.api.dto.PerformanceDashboardResponse;
import com.ewos.performance.api.dto.PromotionRecommendationRequest;
import com.ewos.performance.api.dto.ReviewerAssessmentRequest;
import com.ewos.performance.api.dto.SelfAssessmentRequest;
import com.ewos.performance.api.dto.SubmitAppraisalForApprovalRequest;
import com.ewos.performance.application.AppraisalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/appraisals")
@Tag(name = "Appraisals", description = "Per-employee appraisal lifecycle")
public class AppraisalController {

    private final AppraisalService appraisals;

    public AppraisalController(AppraisalService appraisals) {
        this.appraisals = appraisals;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERF_WRITE')")
    @Operation(summary = "Open an appraisal for an employee within a cycle")
    public ResponseEntity<AppraisalResponse> open(@Valid @RequestBody OpenAppraisalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appraisals.open(req));
    }

    @PostMapping("/{id}/self")
    @PreAuthorize("hasAuthority('PERF_APPRAISE_SELF')")
    @Operation(summary = "Submit the self assessment")
    public AppraisalResponse submitSelf(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody SelfAssessmentRequest req) {
        return appraisals.submitSelf(tenantId, id, req);
    }

    @PostMapping("/{id}/manager")
    @PreAuthorize("hasAuthority('PERF_APPRAISE_MANAGER')")
    @Operation(summary = "Submit the manager assessment")
    public AppraisalResponse submitManager(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ManagerAssessmentRequest req) {
        return appraisals.submitManager(tenantId, id, req);
    }

    @PostMapping("/{id}/reviewer")
    @PreAuthorize("hasAuthority('PERF_APPRAISE_REVIEWER')")
    @Operation(summary = "Submit the reviewer assessment")
    public AppraisalResponse submitReviewer(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ReviewerAssessmentRequest req) {
        return appraisals.submitReviewer(tenantId, id, req);
    }

    @PostMapping("/{id}/calibrate")
    @PreAuthorize("hasAuthority('PERF_CALIBRATE')")
    @Operation(summary = "Record calibrated rating + band")
    public AppraisalResponse calibrate(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody CalibrationRequest req) {
        return appraisals.calibrate(tenantId, id, req);
    }

    @PostMapping("/{id}/submit-for-approval")
    @PreAuthorize("hasAuthority('PERF_CALIBRATE')")
    @Operation(summary = "Submit a calibrated appraisal into the approval workflow")
    public AppraisalResponse submitForApproval(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody SubmitAppraisalForApprovalRequest req) {
        return appraisals.submitForApproval(tenantId, id, req);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PERF_APPROVE')")
    @Operation(summary = "Approve + finalise an appraisal")
    public AppraisalResponse approve(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) AppraisalDecisionRequest req) {
        return appraisals.approve(tenantId, id, req);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('PERF_APPROVE')")
    @Operation(summary = "Reject an appraisal (returns to CALIBRATION)")
    public AppraisalResponse reject(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) AppraisalDecisionRequest req) {
        return appraisals.reject(tenantId, id, req);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PERF_WRITE')")
    @Operation(summary = "Cancel an appraisal")
    public AppraisalResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        return appraisals.cancel(tenantId, id, reason);
    }

    @PostMapping("/{id}/increment")
    @PreAuthorize("hasAuthority('PERF_APPROVE')")
    @Operation(summary = "Record increment recommendation")
    public AppraisalResponse recordIncrement(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody IncrementRecommendationRequest req) {
        return appraisals.recordIncrement(tenantId, id, req);
    }

    @PostMapping("/{id}/promotion")
    @PreAuthorize("hasAuthority('PERF_APPROVE')")
    @Operation(summary = "Record promotion recommendation")
    public AppraisalResponse recordPromotion(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody PromotionRecommendationRequest req) {
        return appraisals.recordPromotion(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Fetch an appraisal by id")
    public AppraisalResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return appraisals.getById(tenantId, id);
    }

    @GetMapping("/by-cycle")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "List all appraisals in a cycle")
    public List<AppraisalResponse> byCycle(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID cycleId) {
        return appraisals.forCycle(tenantId, cycleId);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Aggregate appraisal counts by status for a cycle")
    public PerformanceDashboardResponse dashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID cycleId) {
        return appraisals.dashboard(tenantId, cycleId);
    }

    @GetMapping("/reports/by-cycle")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Per-employee appraisal report for a cycle")
    public List<AppraisalReportRowResponse> report(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID cycleId) {
        return appraisals.reportForCycle(tenantId, cycleId);
    }

    @GetMapping("/reports/bell-curve")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Bell-curve buckets for finalised appraisals in a cycle")
    public List<BellCurveBucketResponse> bellCurve(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID cycleId) {
        return appraisals.bellCurve(tenantId, cycleId);
    }
}
