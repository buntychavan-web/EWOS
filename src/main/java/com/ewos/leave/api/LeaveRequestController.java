package com.ewos.leave.api;

import com.ewos.leave.api.dto.CreateLeaveRequestRequest;
import com.ewos.leave.api.dto.DecideLeaveRequestRequest;
import com.ewos.leave.api.dto.LeaveRequestResponse;
import com.ewos.leave.api.dto.SubmitLeaveRequestRequest;
import com.ewos.leave.application.LeaveRequestService;
import com.ewos.leave.domain.LeaveRequestStatus;
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
@RequestMapping("/api/v1/leave/requests")
@Tag(name = "Leave Requests", description = "Employee leave submissions with workflow approval")
public class LeaveRequestController {

    private final LeaveRequestService service;

    public LeaveRequestController(LeaveRequestService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEAVE_WRITE')")
    @Operation(summary = "Create a DRAFT leave request")
    public ResponseEntity<LeaveRequestResponse> create(
            @Valid @RequestBody CreateLeaveRequestRequest request) {
        LeaveRequestResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/leave/requests/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAVE_READ')")
    @Operation(summary = "Fetch a leave request by ID")
    public LeaveRequestResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('LEAVE_WRITE')")
    @Operation(summary = "Submit a DRAFT request; starts a workflow instance and reserves days")
    public LeaveRequestResponse submit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody SubmitLeaveRequestRequest request) {
        return service.submit(tenantId, id, request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    @Operation(summary = "Approve a SUBMITTED leave request")
    public LeaveRequestResponse approve(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody DecideLeaveRequestRequest request) {
        return service.approve(tenantId, id, request);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    @Operation(summary = "Reject a SUBMITTED leave request with a reason")
    public LeaveRequestResponse reject(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody DecideLeaveRequestRequest request) {
        return service.reject(tenantId, id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('LEAVE_WRITE')")
    @Operation(summary = "Cancel a DRAFT or SUBMITTED request (APPROVED requires LEAVE_ADMIN)")
    public LeaveRequestResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.cancel(tenantId, id, false);
    }

    @PostMapping("/{id}/admin-cancel")
    @PreAuthorize("hasAuthority('LEAVE_ADMIN')")
    @Operation(summary = "Admin-cancel any request in any status except CANCELLED / REJECTED")
    public LeaveRequestResponse adminCancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.cancel(tenantId, id, true);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('LEAVE_READ')")
    @Operation(summary = "List an employee's leave requests (most-recent-first)")
    public List<LeaveRequestResponse> forEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.forEmployee(tenantId, employeeId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LEAVE_READ')")
    @Operation(summary = "Filter tenant leave requests by status")
    public List<LeaveRequestResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam LeaveRequestStatus status) {
        return service.byStatus(tenantId, status);
    }
}
