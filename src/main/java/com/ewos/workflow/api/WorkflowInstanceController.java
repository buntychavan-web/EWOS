package com.ewos.workflow.api;

import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.api.dto.WorkflowHistoryResponse;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow/instances")
@Tag(name = "Workflow Instances", description = "Runtime for workflow instances")
public class WorkflowInstanceController {

    private final WorkflowInstanceService service;

    public WorkflowInstanceController(WorkflowInstanceService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WF_WRITE')")
    @Operation(summary = "Start a workflow instance against a subject entity")
    public ResponseEntity<WorkflowInstanceResponse> start(
            @Valid @RequestBody StartInstanceRequest request) {
        WorkflowInstanceResponse created = service.start(request);
        return ResponseEntity.created(URI.create("/api/v1/workflow/instances/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('WF_READ')")
    @Operation(summary = "Fetch an instance by ID")
    public WorkflowInstanceResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('WF_READ')")
    @Operation(
            summary =
                    "Find all instances for a subject entity (e.g. all workflows on one leave request)")
    public List<WorkflowInstanceResponse> findBySubject(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam("subjectType") String subjectType,
            @RequestParam("subjectId") UUID subjectId) {
        return service.findBySubject(tenantId, subjectType, subjectId);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('WF_READ')")
    @Operation(summary = "Full transition history of an instance")
    public List<WorkflowHistoryResponse> history(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.historyOf(tenantId, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('WF_ADMIN')")
    @Operation(summary = "Cancel a running instance")
    public WorkflowInstanceResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam(value = "notes", required = false) String notes) {
        return service.cancel(tenantId, id, notes);
    }
}
