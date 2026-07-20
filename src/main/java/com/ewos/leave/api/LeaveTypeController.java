package com.ewos.leave.api;

import com.ewos.leave.api.dto.CreateLeaveTypeRequest;
import com.ewos.leave.api.dto.LeaveTypeResponse;
import com.ewos.leave.api.dto.UpdateLeaveTypeRequest;
import com.ewos.leave.application.LeaveTypeService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leave/types")
@Tag(name = "Leave Types", description = "Per-tenant catalogue of leave categories")
public class LeaveTypeController {

    private final LeaveTypeService service;

    public LeaveTypeController(LeaveTypeService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEAVE_ADMIN')")
    @Operation(summary = "Create a new leave type")
    public ResponseEntity<LeaveTypeResponse> create(
            @Valid @RequestBody CreateLeaveTypeRequest request) {
        LeaveTypeResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/leave/types/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAVE_READ')")
    @Operation(summary = "Fetch by ID (Redis-cached)")
    public LeaveTypeResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LEAVE_READ')")
    @Operation(summary = "List all leave types for the tenant")
    public List<LeaveTypeResponse> list(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.list(tenantId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAVE_ADMIN')")
    @Operation(summary = "Update mutable fields")
    public LeaveTypeResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLeaveTypeRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAVE_ADMIN')")
    @Operation(summary = "Soft-delete a leave type")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
