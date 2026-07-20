package com.ewos.workflow.api;

import com.ewos.workflow.api.dto.AssignTaskRequest;
import com.ewos.workflow.api.dto.CompleteTaskRequest;
import com.ewos.workflow.api.dto.WorkflowTaskResponse;
import com.ewos.workflow.application.WorkflowTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/workflow/tasks")
@Tag(name = "Workflow Tasks", description = "Human tasks emitted by running workflow instances")
public class WorkflowTaskController {

    private final WorkflowTaskService service;

    public WorkflowTaskController(WorkflowTaskService service) {
        this.service = service;
    }

    @PostMapping("/for-instance/{instanceId}")
    @PreAuthorize("hasAuthority('WF_WRITE')")
    @Operation(summary = "Assign a new task on an instance's current state")
    public WorkflowTaskResponse assign(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID instanceId,
            @Valid @RequestBody AssignTaskRequest request) {
        return service.assign(tenantId, instanceId, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('WF_READ')")
    @Operation(summary = "Fetch a task by ID")
    public WorkflowTaskResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAuthority('WF_ACT')")
    @Operation(summary = "Claim an OPEN task as the current user")
    public WorkflowTaskResponse claim(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.claim(tenantId, id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('WF_ACT')")
    @Operation(summary = "Complete a task with an action code; drives the instance forward")
    public WorkflowTaskResponse complete(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody CompleteTaskRequest request) {
        return service.complete(tenantId, id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('WF_ADMIN')")
    @Operation(summary = "Cancel an open task without advancing the instance")
    public ResponseEntity<Void> cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.cancel(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('WF_ACT')")
    @Operation(summary = "List OPEN or CLAIMED tasks assigned to a specific actor")
    public List<WorkflowTaskResponse> myTasks(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam("actorId") UUID actorId) {
        return service.myOpenTasks(tenantId, actorId);
    }

    @GetMapping("/by-role")
    @PreAuthorize("hasAuthority('WF_ACT')")
    @Operation(summary = "List OPEN or CLAIMED tasks assigned to a role code")
    public List<WorkflowTaskResponse> byRole(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam("roleCode") String roleCode) {
        return service.openTasksForRole(tenantId, roleCode);
    }

    @GetMapping("/of-instance/{instanceId}")
    @PreAuthorize("hasAuthority('WF_READ')")
    @Operation(summary = "All tasks (of any status) on a given instance")
    public List<WorkflowTaskResponse> ofInstance(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID instanceId) {
        return service.tasksOfInstance(tenantId, instanceId);
    }
}
