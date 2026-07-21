package com.ewos.workflow.api;

import com.ewos.workflow.api.dto.CreateWorkflowDefinitionRequest;
import com.ewos.workflow.api.dto.WorkflowDefinitionResponse;
import com.ewos.workflow.application.WorkflowDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow/definitions")
@Tag(name = "Workflow Definitions", description = "Authoring API for workflow state graphs")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService service;

    public WorkflowDefinitionController(WorkflowDefinitionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WF_WRITE')")
    @Operation(summary = "Publish a new workflow definition")
    public ResponseEntity<WorkflowDefinitionResponse> create(
            @Valid @RequestBody CreateWorkflowDefinitionRequest request) {
        WorkflowDefinitionResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/workflow/definitions/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('WF_READ')")
    @Operation(summary = "Fetch a definition by ID (Redis-cached)")
    public WorkflowDefinitionResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('WF_READ')")
    @Operation(summary = "List definitions for the tenant")
    public List<WorkflowDefinitionResponse> list(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.list(tenantId);
    }

    @PostMapping("/{id}/active")
    @PreAuthorize("hasAuthority('WF_ADMIN')")
    @Operation(summary = "Activate or deactivate a definition")
    public WorkflowDefinitionResponse setActive(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam("active") boolean active) {
        return service.setActive(tenantId, id, active);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('WF_ADMIN')")
    @Operation(summary = "Soft-delete a definition")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
