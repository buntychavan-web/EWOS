package com.ewos.performance.api;

import com.ewos.performance.api.dto.AppraisalResponse;
import com.ewos.performance.api.dto.ManagerAssessmentRequest;
import com.ewos.performance.api.dto.ReviewerAssessmentRequest;
import com.ewos.performance.api.dto.SelfAssessmentRequest;
import com.ewos.performance.application.PerformanceSelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 24A — Employee Self-Service over Performance. Requires only authentication (no {@code
 * PERF_*} permission): every method resolves the caller's own employee record and is scoped to it,
 * so it is safe to expose to every employee (self-assessment + own appraisal visibility) and every
 * manager/reviewer (their own reports' pending reviews) without granting the broad, company-wide
 * {@code PERF_READ}/{@code PERF_APPRAISE_*} permissions. Mirrors {@code
 * OnboardingSelfServiceController} (Sprint 23A) exactly.
 */
@RestController
@RequestMapping("/api/v1/performance/self-service")
@Tag(
        name = "Performance Self-Service",
        description = "An employee's own appraisals and pending reviews")
public class PerformanceSelfServiceController {

    private final PerformanceSelfService service;

    public PerformanceSelfServiceController(PerformanceSelfService service) {
        this.service = service;
    }

    @GetMapping("/appraisals")
    @Operation(summary = "The caller's own appraisals, across cycles")
    public List<AppraisalResponse> myAppraisals() {
        return service.myAppraisals();
    }

    @GetMapping("/appraisals/{id}")
    @Operation(summary = "One of the caller's own appraisals (as employee, manager, or reviewer)")
    public AppraisalResponse myAppraisal(@PathVariable UUID id) {
        return service.myAppraisal(id);
    }

    @PostMapping("/appraisals/{id}/self")
    @Operation(summary = "Submit the caller's own self assessment")
    public AppraisalResponse submitMySelfAssessment(
            @PathVariable UUID id, @Valid @RequestBody SelfAssessmentRequest req) {
        return service.submitMySelfAssessment(id, req);
    }

    @GetMapping("/pending-manager-review")
    @Operation(summary = "Appraisals awaiting the caller's own manager assessment")
    public List<AppraisalResponse> pendingMyManagerReview() {
        return service.pendingMyManagerReview();
    }

    @PostMapping("/appraisals/{id}/manager")
    @Operation(summary = "Submit the caller's own manager assessment for a direct report")
    public AppraisalResponse submitMyManagerAssessment(
            @PathVariable UUID id, @Valid @RequestBody ManagerAssessmentRequest req) {
        return service.submitMyManagerAssessment(id, req);
    }

    @GetMapping("/pending-reviewer-review")
    @Operation(summary = "Appraisals awaiting the caller's own reviewer assessment")
    public List<AppraisalResponse> pendingMyReviewerReview() {
        return service.pendingMyReviewerReview();
    }

    @PostMapping("/appraisals/{id}/reviewer")
    @Operation(summary = "Submit the caller's own reviewer assessment")
    public AppraisalResponse submitMyReviewerAssessment(
            @PathVariable UUID id, @Valid @RequestBody ReviewerAssessmentRequest req) {
        return service.submitMyReviewerAssessment(id, req);
    }
}
