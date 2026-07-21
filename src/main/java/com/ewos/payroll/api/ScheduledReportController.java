package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateScheduledReportRequest;
import com.ewos.payroll.api.dto.ScheduledReportResponse;
import com.ewos.payroll.application.ScheduledReportService;
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
@RequestMapping("/api/v1/payroll/scheduled-reports")
@Tag(name = "Scheduled Payroll Reports", description = "Cron-driven recurring report declarations")
public class ScheduledReportController {

    private final ScheduledReportService service;

    public ScheduledReportController(ScheduledReportService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Declare a new scheduled report")
    public ResponseEntity<ScheduledReportResponse> create(
            @Valid @RequestBody CreateScheduledReportRequest r) {
        ScheduledReportResponse created = service.create(r);
        return ResponseEntity.created(
                        URI.create("/api/v1/payroll/scheduled-reports/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Fetch a scheduled report")
    public ScheduledReportResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "List scheduled reports for a company")
    public List<ScheduledReportResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return service.list(tenantId, companyId);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Deactivate a scheduled report (stops future firings)")
    public ResponseEntity<Void> deactivate(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.deactivate(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Soft-delete a scheduled report")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
