package com.ewos.goals.api;

import com.ewos.goals.api.dto.CreateGoalRequest;
import com.ewos.goals.api.dto.GoalDashboardResponse;
import com.ewos.goals.api.dto.GoalProgressResponse;
import com.ewos.goals.api.dto.GoalReportRowResponse;
import com.ewos.goals.api.dto.GoalResponse;
import com.ewos.goals.api.dto.GoalReviewRequest;
import com.ewos.goals.api.dto.ProgressUpdateRequest;
import com.ewos.goals.api.dto.UpdateGoalRequest;
import com.ewos.goals.application.GoalService;
import com.ewos.goals.domain.GoalScope;
import com.ewos.goals.domain.GoalStatus;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goals")
@Tag(name = "Goals", description = "Assigned goals lifecycle (KRA / KPI / OKR)")
public class GoalController {

    private final GoalService goals;

    public GoalController(GoalService goals) {
        this.goals = goals;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GOAL_WRITE')")
    @Operation(summary = "Create a goal (starts in DRAFT)")
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody CreateGoalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goals.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('GOAL_WRITE')")
    @Operation(summary = "Update a goal")
    public GoalResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGoalRequest req) {
        return goals.update(tenantId, id, req);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('GOAL_WRITE')")
    @Operation(summary = "Assign a DRAFT goal (moves to ASSIGNED)")
    public GoalResponse assign(@RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return goals.assign(tenantId, id);
    }

    @PostMapping("/{id}/progress")
    @PreAuthorize("hasAuthority('GOAL_UPDATE_PROGRESS')")
    @Operation(summary = "Record a progress update")
    public ResponseEntity<GoalProgressResponse> recordProgress(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ProgressUpdateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(goals.recordProgress(tenantId, id, req));
    }

    @PostMapping("/{id}/submit-review")
    @PreAuthorize("hasAuthority('GOAL_WRITE')")
    @Operation(summary = "Submit a goal for review (moves to UNDER_REVIEW)")
    public GoalResponse submitForReview(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return goals.submitForReview(tenantId, id);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('GOAL_REVIEW')")
    @Operation(summary = "Score + review a goal")
    public GoalResponse review(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody GoalReviewRequest req) {
        return goals.review(tenantId, id, req);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('GOAL_REVIEW')")
    @Operation(summary = "Complete a goal (terminal)")
    public GoalResponse complete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return goals.complete(tenantId, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('GOAL_WRITE')")
    @Operation(summary = "Cancel a goal (terminal)")
    public GoalResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        return goals.cancel(tenantId, id, reason);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GOAL_READ')")
    @Operation(summary = "Fetch a goal by id")
    public GoalResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return goals.getById(tenantId, id);
    }

    @GetMapping("/by-employee/{employeeId}")
    @PreAuthorize("hasAuthority('GOAL_READ')")
    @Operation(summary = "List goals for a given employee")
    public List<GoalResponse> byEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return goals.byEmployee(tenantId, employeeId);
    }

    @GetMapping("/by-status")
    @PreAuthorize("hasAuthority('GOAL_READ')")
    @Operation(summary = "List goals by status")
    public List<GoalResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam GoalStatus status) {
        return goals.byStatus(tenantId, companyId, status);
    }

    @GetMapping("/by-scope")
    @PreAuthorize("hasAuthority('GOAL_READ')")
    @Operation(summary = "List goals by scope")
    public List<GoalResponse> byScope(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam GoalScope scope) {
        return goals.byScope(tenantId, companyId, scope);
    }

    @GetMapping("/by-cycle")
    @PreAuthorize("hasAuthority('GOAL_READ')")
    @Operation(summary = "List goals for a performance cycle")
    public List<GoalResponse> byCycle(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID performanceCycleId) {
        return goals.forCycle(tenantId, performanceCycleId);
    }

    @GetMapping("/{id}/progress")
    @PreAuthorize("hasAuthority('GOAL_READ')")
    @Operation(summary = "List progress history for a goal")
    public List<GoalProgressResponse> progressHistory(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return goals.progressHistory(tenantId, id);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('GOAL_READ')")
    @Operation(summary = "Aggregate goal counts by status + scope")
    public GoalDashboardResponse dashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return goals.dashboard(tenantId, companyId);
    }

    @GetMapping("/reports/by-status")
    @PreAuthorize("hasAuthority('GOAL_READ')")
    @Operation(summary = "Per-goal report filtered by status")
    public List<GoalReportRowResponse> report(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam GoalStatus status) {
        return goals.reportByStatus(tenantId, companyId, status);
    }
}
