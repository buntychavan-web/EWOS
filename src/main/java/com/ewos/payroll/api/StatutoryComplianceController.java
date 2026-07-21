package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.FileChallanRequest;
import com.ewos.payroll.api.dto.PayChallanRequest;
import com.ewos.payroll.api.dto.RollUpChallanRequest;
import com.ewos.payroll.api.dto.StatutoryChallanResponse;
import com.ewos.payroll.api.dto.StatutoryDeductionResponse;
import com.ewos.payroll.application.StatutoryChallanService;
import com.ewos.payroll.application.StatutoryDeductionService;
import com.ewos.payroll.domain.StatutoryChallanStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/v1/payroll")
@Tag(
        name = "Statutory Compliance",
        description = "Per-payslip statutory deductions + monthly challans")
public class StatutoryComplianceController {

    private final StatutoryDeductionService deductions;
    private final StatutoryChallanService challans;

    public StatutoryComplianceController(
            StatutoryDeductionService deductions, StatutoryChallanService challans) {
        this.deductions = deductions;
        this.challans = challans;
    }

    // ---- Deductions ---------------------------------------------------

    @PostMapping("/deductions/run/{runId}/extract")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Extract statutory deductions from every payslip on a run (idempotent)")
    public Map<String, Object> extractForRun(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID runId) {
        int inserted = deductions.extractForRun(tenantId, runId);
        return Map.of("runId", runId, "insertedCount", inserted);
    }

    @GetMapping("/deductions/payslip/{payslipId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "All statutory deductions attached to a payslip")
    public List<StatutoryDeductionResponse> forPayslip(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID payslipId) {
        return deductions.forPayslip(tenantId, payslipId);
    }

    @GetMapping("/deductions/run/{runId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "All statutory deductions for a payroll run")
    public List<StatutoryDeductionResponse> forRun(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID runId) {
        return deductions.forRun(tenantId, runId);
    }

    @GetMapping("/deductions/employee/{employeeId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Statutory deductions for an employee in a month (YYYYMM)")
    public List<StatutoryDeductionResponse> forEmployeeMonth(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID employeeId,
            @RequestParam int periodMonth) {
        return deductions.forEmployeeMonth(tenantId, employeeId, periodMonth);
    }

    // ---- Challans -----------------------------------------------------

    @PostMapping("/challans/rollup")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Roll unattached deductions into a challan; idempotent per scope")
    public ResponseEntity<StatutoryChallanResponse> rollUp(
            @Valid @RequestBody RollUpChallanRequest request) {
        StatutoryChallanResponse rolled = challans.rollUp(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/challans/" + rolled.id()))
                .body(rolled);
    }

    @PostMapping("/challans/{id}/file")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "File a DRAFT challan with the statutory authority")
    public StatutoryChallanResponse file(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody FileChallanRequest request) {
        return challans.file(tenantId, id, request.filingReference());
    }

    @PostMapping("/challans/{id}/pay")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Record payment against a FILED challan")
    public StatutoryChallanResponse pay(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody PayChallanRequest request) {
        return challans.pay(tenantId, id, request.paymentReference());
    }

    @PostMapping("/challans/{id}/cancel")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Cancel a non-PAID challan")
    public StatutoryChallanResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return challans.cancel(tenantId, id);
    }

    @GetMapping("/challans/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch a single challan by ID")
    public StatutoryChallanResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return challans.getById(tenantId, id);
    }

    @GetMapping("/challans")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List challans for a company in a given month (YYYYMM)")
    public List<StatutoryChallanResponse> forMonth(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam int periodMonth) {
        return challans.forMonth(tenantId, companyId, periodMonth);
    }

    @GetMapping("/challans/status")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List challans by status for a company")
    public List<StatutoryChallanResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam StatutoryChallanStatus status) {
        return challans.byStatus(tenantId, companyId, status);
    }
}
