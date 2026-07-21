package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.PayrollRunResponse;
import com.ewos.payroll.api.dto.StartPayrollRunRequest;
import com.ewos.payroll.api.dto.StartSupplementaryRunRequest;
import com.ewos.payroll.application.PayrollRunService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/runs")
@Tag(name = "Payroll Runs", description = "Compute cycles over a locked period")
public class PayrollRunController {

    private final PayrollRunService service;

    public PayrollRunController(PayrollRunService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    @Operation(summary = "Start a payroll run over a LOCKED period; generates DRAFT payslips")
    public ResponseEntity<PayrollRunResponse> start(
            @Valid @RequestBody StartPayrollRunRequest request) {
        PayrollRunResponse created = service.start(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/runs/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    @Operation(summary = "Finalize a COMPLETED run; flips every payslip to FINALIZED")
    public PayrollRunResponse finalizeRun(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.finalizeRun(tenantId, id);
    }

    @PostMapping("/supplementary")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    @Operation(summary = "Start an off-cycle SUPPLEMENTARY run for selected employees")
    public ResponseEntity<PayrollRunResponse> startSupplementary(
            @Valid @RequestBody StartSupplementaryRunRequest request) {
        PayrollRunResponse created =
                service.startSupplementary(
                        request.tenantId(),
                        request.companyId(),
                        request.payrollPeriodId(),
                        request.employeeIds());
        return ResponseEntity.created(URI.create("/api/v1/payroll/runs/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/freeze")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Freeze a FINALIZED run; terminal lock, no adjustments permitted after")
    public PayrollRunResponse freeze(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.freeze(tenantId, id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID")
    public PayrollRunResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/period/{periodId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List runs for a payroll period")
    public List<PayrollRunResponse> forPeriod(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID periodId) {
        return service.forPeriod(tenantId, periodId);
    }
}
