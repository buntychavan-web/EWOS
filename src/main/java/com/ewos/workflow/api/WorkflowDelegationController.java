package com.ewos.workflow.api;

import com.ewos.workflow.api.dto.CreateDelegationRequest;
import com.ewos.workflow.api.dto.WorkflowDelegationResponse;
import com.ewos.workflow.application.WorkflowDelegationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service task delegation — auth-only, no {@code @PreAuthorize}, mirrors the leave/attendance
 * self-service controllers: a caller only ever creates, lists, or revokes their own delegations.
 */
@RestController
@RequestMapping("/api/v1/workflow/delegations")
@Tag(name = "Workflow Delegations", description = "Delegate your task inbox to another user")
public class WorkflowDelegationController {

    private final WorkflowDelegationService service;

    public WorkflowDelegationController(WorkflowDelegationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Delegate my task inbox to another user for a time window")
    public WorkflowDelegationResponse create(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateDelegationRequest request) {
        return service.create(tenantId, request);
    }

    @GetMapping("/mine")
    @Operation(summary = "List delegations I have granted")
    public List<WorkflowDelegationResponse> mine(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.mine(tenantId);
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revoke a delegation I granted")
    public ResponseEntity<Void> revoke(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.revoke(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
