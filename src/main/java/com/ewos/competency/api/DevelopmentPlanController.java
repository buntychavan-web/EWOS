package com.ewos.competency.api;

import com.ewos.competency.api.dto.ActionResponse;
import com.ewos.competency.api.dto.AddActionRequest;
import com.ewos.competency.api.dto.CompetencyDashboardResponse;
import com.ewos.competency.api.dto.CreatePlanRequest;
import com.ewos.competency.api.dto.PlanResponse;
import com.ewos.competency.application.DevelopmentPlanService;
import com.ewos.competency.domain.DevelopmentPlanStatus;
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
@RequestMapping("/api/v1/development-plans")
@Tag(name = "Development Plans", description = "Employee development plans + actions")
public class DevelopmentPlanController {

    private final DevelopmentPlanService plans;

    public DevelopmentPlanController(DevelopmentPlanService plans) {
        this.plans = plans;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COMPETENCY_PLAN')")
    @Operation(summary = "Create a development plan (DRAFT)")
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody CreatePlanRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plans.create(req));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('COMPETENCY_PLAN')")
    @Operation(summary = "Activate a plan (DRAFT → ACTIVE)")
    public PlanResponse activate(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return plans.activate(tenantId, id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('COMPETENCY_PLAN')")
    @Operation(summary = "Complete a plan (terminal)")
    public PlanResponse complete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return plans.complete(tenantId, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('COMPETENCY_PLAN')")
    @Operation(summary = "Cancel a plan (terminal)")
    public PlanResponse cancel(@RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return plans.cancel(tenantId, id);
    }

    @PostMapping("/{id}/actions")
    @PreAuthorize("hasAuthority('COMPETENCY_PLAN')")
    @Operation(summary = "Add an action to a plan")
    public ResponseEntity<ActionResponse> addAction(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AddActionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plans.addAction(tenantId, id, req));
    }

    @PostMapping("/actions/{actionId}/complete")
    @PreAuthorize("hasAuthority('COMPETENCY_PLAN')")
    @Operation(summary = "Mark an action complete")
    public ActionResponse completeAction(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID actionId) {
        return plans.completeAction(tenantId, actionId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "Fetch a plan by id")
    public PlanResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return plans.getById(tenantId, id);
    }

    @GetMapping("/{id}/actions")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "List actions on a plan")
    public List<ActionResponse> actionsFor(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return plans.actionsFor(tenantId, id);
    }

    @GetMapping("/by-employee/{employeeId}")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "List plans for an employee")
    public List<PlanResponse> forEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return plans.forEmployee(tenantId, employeeId);
    }

    @GetMapping("/by-status")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "List plans by status")
    public List<PlanResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam DevelopmentPlanStatus status) {
        return plans.byStatus(tenantId, companyId, status);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "Aggregate competency + plan counts for a company")
    public CompetencyDashboardResponse dashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return plans.dashboard(tenantId, companyId);
    }
}
