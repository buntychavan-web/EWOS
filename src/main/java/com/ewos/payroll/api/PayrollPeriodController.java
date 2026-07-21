package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreatePayrollPeriodRequest;
import com.ewos.payroll.api.dto.PayrollPeriodResponse;
import com.ewos.payroll.api.dto.UpdatePayrollPeriodRequest;
import com.ewos.payroll.application.PayrollPeriodService;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/periods")
@Tag(name = "Payroll Periods", description = "Per-company payroll windows and their lifecycle")
public class PayrollPeriodController {

    private final PayrollPeriodService service;

    public PayrollPeriodController(PayrollPeriodService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_WRITE')")
    @Operation(summary = "Create a new payroll period (starts in OPEN)")
    public ResponseEntity<PayrollPeriodResponse> create(
            @Valid @RequestBody CreatePayrollPeriodRequest request) {
        PayrollPeriodResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/periods/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID")
    public PayrollPeriodResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List all periods for a company")
    public List<PayrollPeriodResponse> forCompany(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID companyId) {
        return service.forCompany(tenantId, companyId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List by status")
    public List<PayrollPeriodResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam PayrollPeriodStatus status) {
        return service.byStatus(tenantId, status);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_WRITE')")
    @Operation(summary = "Update mutable fields; only OPEN periods can be edited")
    public PayrollPeriodResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePayrollPeriodRequest request) {
        return service.update(tenantId, id, request);
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    @Operation(summary = "Lock the period so a run can start")
    public PayrollPeriodResponse lock(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.lock(tenantId, id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Close the period; runs must already be FINALIZED")
    public PayrollPeriodResponse close(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.close(tenantId, id);
    }
}
