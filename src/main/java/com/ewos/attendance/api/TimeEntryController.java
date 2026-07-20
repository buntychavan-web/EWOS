package com.ewos.attendance.api;

import com.ewos.attendance.api.dto.CreateTimeEntryRequest;
import com.ewos.attendance.api.dto.TimeEntryResponse;
import com.ewos.attendance.application.TimeEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/attendance/time-entries")
@Tag(name = "Time Entries", description = "Clock events (IN / OUT / BREAK_START / BREAK_END)")
public class TimeEntryController {

    private final TimeEntryService service;

    public TimeEntryController(TimeEntryService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ATT_WRITE')")
    @Operation(summary = "Record a new time entry (or a correction of an existing one)")
    public ResponseEntity<TimeEntryResponse> record(
            @Valid @RequestBody CreateTimeEntryRequest request) {
        TimeEntryResponse created = service.record(request);
        return ResponseEntity.created(URI.create("/api/v1/attendance/time-entries/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ATT_READ')")
    @Operation(summary = "Fetch a time entry by ID")
    public TimeEntryResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('ATT_READ')")
    @Operation(summary = "Time entries for an employee in a time range")
    public List<TimeEntryResponse> forEmployeeInRange(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.forEmployeeInRange(tenantId, employeeId, from, to);
    }

    @GetMapping("/employee/{employeeId}/recent")
    @PreAuthorize("hasAuthority('ATT_READ')")
    @Operation(summary = "Recent time entries for an employee (most-recent-first)")
    public List<TimeEntryResponse> recentForEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.recentForEmployee(tenantId, employeeId);
    }
}
