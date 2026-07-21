package com.ewos.onboarding.api;

import com.ewos.onboarding.api.dto.AddOnboardingTaskRequest;
import com.ewos.onboarding.api.dto.AssignPlanRolesRequest;
import com.ewos.onboarding.api.dto.CreateOnboardingPlanRequest;
import com.ewos.onboarding.api.dto.OnboardingPlanResponse;
import com.ewos.onboarding.api.dto.OnboardingTaskInstanceResponse;
import com.ewos.onboarding.api.dto.UpdateOnboardingTaskStatusRequest;
import com.ewos.onboarding.application.OnboardingPlanService;
import com.ewos.onboarding.domain.OnboardingPlanStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding Plans", description = "Post-joining plans + tasks")
public class OnboardingPlanController {

    private final OnboardingPlanService plans;

    public OnboardingPlanController(OnboardingPlanService plans) {
        this.plans = plans;
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('ONBOARDING_WRITE')")
    @Operation(summary = "Create an onboarding plan (idempotent per employee)")
    public ResponseEntity<OnboardingPlanResponse> create(
            @Valid @RequestBody CreateOnboardingPlanRequest req) {
        OnboardingPlanResponse created = plans.create(req);
        return ResponseEntity.created(URI.create("/api/v1/onboarding/plans/" + created.id()))
                .body(created);
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    @Operation(summary = "Fetch a plan by ID")
    public OnboardingPlanResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return plans.getById(tenantId, id);
    }

    @GetMapping("/plans/by-employee/{employeeId}")
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    @Operation(summary = "Fetch the plan for a specific employee")
    public OnboardingPlanResponse forEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return plans.forEmployee(tenantId, employeeId);
    }

    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    @Operation(summary = "List plans for a company by status")
    public List<OnboardingPlanResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam OnboardingPlanStatus status) {
        return plans.byStatus(tenantId, companyId, status);
    }

    @PostMapping("/plans/{id}/start")
    @PreAuthorize("hasAuthority('ONBOARDING_WRITE')")
    @Operation(summary = "Mark the plan IN_PROGRESS")
    public OnboardingPlanResponse start(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return plans.start(tenantId, id);
    }

    @PostMapping("/plans/{id}/complete")
    @PreAuthorize("hasAuthority('ONBOARDING_WRITE')")
    @Operation(summary = "Complete the plan (all mandatory tasks must be terminal)")
    public OnboardingPlanResponse complete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return plans.complete(tenantId, id);
    }

    @PostMapping("/plans/{id}/cancel")
    @PreAuthorize("hasAuthority('ONBOARDING_WRITE')")
    @Operation(summary = "Cancel the plan with a reason")
    public OnboardingPlanResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) String reason) {
        return plans.cancel(tenantId, id, reason);
    }

    @PutMapping("/plans/{id}/roles")
    @PreAuthorize("hasAuthority('ONBOARDING_WRITE')")
    @Operation(summary = "Assign / change the manager and buddy on a plan")
    public OnboardingPlanResponse assignRoles(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody AssignPlanRolesRequest req) {
        return plans.assignRoles(tenantId, id, req);
    }

    @PostMapping("/plans/{id}/tasks")
    @PreAuthorize("hasAuthority('ONBOARDING_WRITE')")
    @Operation(summary = "Add a task to a plan (from template or ad-hoc)")
    public OnboardingTaskInstanceResponse addTask(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AddOnboardingTaskRequest req) {
        return plans.addTask(tenantId, id, req);
    }

    @GetMapping("/plans/{id}/tasks")
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    @Operation(summary = "List tasks for a plan")
    public List<OnboardingTaskInstanceResponse> listTasks(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return plans.tasksFor(tenantId, id);
    }

    @PutMapping("/tasks/{id}/status")
    @PreAuthorize("hasAuthority('ONBOARDING_WRITE')")
    @Operation(summary = "Update the status of an onboarding task")
    public OnboardingTaskInstanceResponse updateTaskStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOnboardingTaskStatusRequest req) {
        return plans.updateTaskStatus(tenantId, id, req);
    }

    @PostMapping("/tasks/{id}/remind")
    @PreAuthorize("hasAuthority('ONBOARDING_WRITE')")
    @Operation(summary = "Emit a reminder for an outstanding task")
    public OnboardingTaskInstanceResponse remindTask(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return plans.remindTask(tenantId, id);
    }
}
