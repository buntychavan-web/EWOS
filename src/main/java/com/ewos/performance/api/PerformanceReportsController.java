package com.ewos.performance.api;

import com.ewos.performance.api.dto.AppraisalReportRowResponse;
import com.ewos.performance.api.dto.CalibrationSummaryResponse;
import com.ewos.performance.api.dto.FinalRatingSummaryResponse;
import com.ewos.performance.api.dto.OrgUnitProgressResponse;
import com.ewos.performance.api.dto.RatingDistributionBucketResponse;
import com.ewos.performance.application.PerformanceReportsService;
import com.ewos.performance.domain.AppraisalStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 24B — Performance reporting APIs. Every list endpoint accepts a standard Spring {@link
 * Pageable} (page/size/sort query params), so every row shape here is already Excel-export-ready:
 * flat records with no nested objects, stable column ordering, sortable/filterable server-side
 * rather than requiring the caller to page through everything client-side first.
 */
@RestController
@RequestMapping("/api/v1/performance/reports")
@Tag(name = "Performance Reports", description = "Cycle progress, distribution, and status reports")
public class PerformanceReportsController {

    private final PerformanceReportsService reports;

    public PerformanceReportsController(PerformanceReportsService reports) {
        this.reports = reports;
    }

    @GetMapping("/pending-self-reviews")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Appraisals pending self review, paginated")
    public Page<AppraisalReportRowResponse> pendingSelfReviews(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID cycleId,
            Pageable pageable) {
        return reports.pendingSelfReviews(tenantId, cycleId, pageable);
    }

    @GetMapping("/pending-manager-reviews")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Appraisals pending manager review, paginated")
    public Page<AppraisalReportRowResponse> pendingManagerReviews(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID cycleId,
            Pageable pageable) {
        return reports.pendingManagerReviews(tenantId, cycleId, pageable);
    }

    @GetMapping("/pending-reviewer-reviews")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Appraisals pending reviewer review, paginated")
    public Page<AppraisalReportRowResponse> pendingReviewerReviews(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID cycleId,
            Pageable pageable) {
        return reports.pendingReviewerReviews(tenantId, cycleId, pageable);
    }

    @GetMapping("/employee-status")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Per-employee appraisal status, paginated/sorted/filtered")
    public Page<AppraisalReportRowResponse> employeeStatusReport(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID cycleId,
            @RequestParam(required = false) AppraisalStatus status,
            @RequestParam(required = false) UUID orgUnitId,
            @RequestParam(defaultValue = "true") boolean includeDescendants,
            Pageable pageable) {
        return reports.employeeStatusReport(
                tenantId, cycleId, status, orgUnitId, includeDescendants, pageable);
    }

    @GetMapping("/org-unit-progress")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(
            summary = "Department/Business Unit progress",
            description =
                    "One row per direct child of rootOrgUnitId (or every top-level unit if"
                            + " omitted), each aggregating its whole subtree — pass a department's"
                            + " parent for Department Progress, or the company root for Business"
                            + " Unit Progress.")
    public List<OrgUnitProgressResponse> orgUnitProgress(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID cycleId,
            @RequestParam(required = false) UUID rootOrgUnitId) {
        return reports.orgUnitProgress(tenantId, cycleId, rootOrgUnitId);
    }

    @GetMapping("/rating-distribution")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Finalised appraisals bucketed by whole-number final rating")
    public List<RatingDistributionBucketResponse> ratingDistribution(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID cycleId) {
        return reports.ratingDistribution(tenantId, cycleId);
    }

    @GetMapping("/calibration-summary")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Reviewer-vs-calibrated rating shift summary")
    public CalibrationSummaryResponse calibrationSummary(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID cycleId) {
        return reports.calibrationSummary(tenantId, cycleId);
    }

    @GetMapping("/final-rating-summary")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Final band, increment, and promotion breakdown")
    public FinalRatingSummaryResponse finalRatingSummary(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID cycleId) {
        return reports.finalRatingSummary(tenantId, cycleId);
    }
}
