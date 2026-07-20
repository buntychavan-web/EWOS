package com.ewos.attendance.api;

import com.ewos.attendance.api.dto.DecideTimesheetRequest;
import com.ewos.attendance.api.dto.OpenTimesheetRequest;
import com.ewos.attendance.api.dto.SubmitTimesheetRequest;
import com.ewos.attendance.api.dto.TimesheetResponse;
import com.ewos.attendance.application.TimesheetService;
import com.ewos.attendance.domain.TimesheetStatus;
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
@RequestMapping("/api/v1/attendance/timesheets")
@Tag(name = "Timesheets", description = "Period-scoped timesheet submission and approval")
public class TimesheetController {

    private final TimesheetService service;

    public TimesheetController(TimesheetService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ATT_WRITE')")
    @Operation(summary = "Open (or return the existing) DRAFT timesheet for an employee-period")
    public ResponseEntity<TimesheetResponse> open(
            @Valid @RequestBody OpenTimesheetRequest request) {
        TimesheetResponse ts = service.open(request);
        return ResponseEntity.created(URI.create("/api/v1/attendance/timesheets/" + ts.id()))
                .body(ts);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ATT_READ')")
    @Operation(summary = "Fetch a timesheet by ID")
    public TimesheetResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @PostMapping("/{id}/recompute")
    @PreAuthorize("hasAuthority('ATT_WRITE')")
    @Operation(summary = "Recompute rollups from raw time entries (DRAFT only)")
    public TimesheetResponse recompute(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.recompute(tenantId, id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('ATT_WRITE')")
    @Operation(summary = "Submit a DRAFT timesheet; starts a workflow instance for approval")
    public TimesheetResponse submit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody SubmitTimesheetRequest request) {
        return service.submit(tenantId, id, request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ATT_APPROVE')")
    @Operation(summary = "Approve a SUBMITTED timesheet")
    public TimesheetResponse approve(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody DecideTimesheetRequest request) {
        return service.approve(tenantId, id, request);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ATT_APPROVE')")
    @Operation(summary = "Reject a SUBMITTED timesheet with a reason")
    public TimesheetResponse reject(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody DecideTimesheetRequest request) {
        return service.reject(tenantId, id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ATT_ADMIN')")
    @Operation(summary = "Cancel a timesheet (only DRAFT / SUBMITTED / REJECTED)")
    public TimesheetResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.cancel(tenantId, id);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('ATT_READ')")
    @Operation(summary = "All timesheets for a given employee (most-recent-first)")
    public List<TimesheetResponse> forEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.forEmployee(tenantId, employeeId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ATT_READ')")
    @Operation(summary = "Filter tenant timesheets by status")
    public List<TimesheetResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam TimesheetStatus status) {
        return service.byStatus(tenantId, status);
    }
}
