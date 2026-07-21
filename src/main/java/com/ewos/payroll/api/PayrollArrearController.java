package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.ArrearResponse;
import com.ewos.payroll.api.dto.CreateArrearRequest;
import com.ewos.payroll.application.PayrollArrearService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/arrears")
@Tag(name = "Payroll Arrears", description = "Retro salary adjustments picked up by the next run")
public class PayrollArrearController {

    private final PayrollArrearService service;

    public PayrollArrearController(PayrollArrearService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_WRITE')")
    @Operation(summary = "Queue an arrear for the next payroll run")
    public ResponseEntity<ArrearResponse> create(@Valid @RequestBody CreateArrearRequest request) {
        ArrearResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/arrears/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID")
    public ArrearResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_WRITE')")
    @Operation(summary = "Cancel a pending arrear; applied arrears cannot be cancelled")
    public ResponseEntity<Void> cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.cancel(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "All arrears for an employee (newest first)")
    public List<ArrearResponse> forEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.forEmployee(tenantId, employeeId);
    }

    @GetMapping("/employee/{employeeId}/pending")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Pending (not yet applied) arrears for an employee")
    public List<ArrearResponse> pendingForEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.pendingForEmployee(tenantId, employeeId);
    }

    @GetMapping("/run/{runId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Arrears applied to a payroll run")
    public List<ArrearResponse> forRun(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID runId) {
        return service.forRun(tenantId, runId);
    }
}
