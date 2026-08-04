package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateEmployeeLoanRequest;
import com.ewos.payroll.api.dto.EarlyCloseLoanRequest;
import com.ewos.payroll.api.dto.EmployeeLoanResponse;
import com.ewos.payroll.api.dto.LoanInstallmentResponse;
import com.ewos.payroll.api.dto.QueueDueLoanInstallmentsRequest;
import com.ewos.payroll.application.EmployeeLoanService;
import com.ewos.payroll.application.LoanRecoveryService;
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
import org.springframework.web.bind.annotation.RestController;

/** Sprint 24L item 4 — Loan &amp; Recovery Engine. */
@RestController
@RequestMapping("/api/v1/payroll/loans")
@Tag(name = "Employee Loans", description = "Employee loans, salary advances, and recovery")
public class EmployeeLoanController {

    private final EmployeeLoanService service;
    private final LoanRecoveryService recoveryService;

    public EmployeeLoanController(
            EmployeeLoanService service, LoanRecoveryService recoveryService) {
        this.service = service;
        this.recoveryService = recoveryService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Create a loan/advance and generate its full EMI amortization schedule")
    public ResponseEntity<EmployeeLoanResponse> create(
            @Valid @RequestBody CreateEmployeeLoanRequest request) {
        EmployeeLoanResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/loans/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch a loan by ID")
    public EmployeeLoanResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "All loans for an employee")
    public List<EmployeeLoanResponse> forEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.forEmployee(tenantId, employeeId);
    }

    @GetMapping("/{id}/schedule")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Full amortization schedule — every installment's recovery history")
    public List<LoanInstallmentResponse> schedule(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.schedule(tenantId, id);
    }

    @PostMapping("/{id}/early-closure")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(
            summary =
                    "Foreclose a loan: waive remaining installments and queue the outstanding"
                            + " balance as one immediate payroll deduction")
    public EmployeeLoanResponse earlyClosure(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody EarlyCloseLoanRequest request) {
        return service.earlyClosure(tenantId, id, request);
    }

    @PostMapping("/queue-due-installments")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    @Operation(
            summary =
                    "Queue every due loan installment for these employees as payroll deductions —"
                            + " run before starting payroll for the period")
    public Map<String, Integer> queueDueInstallments(
            @Valid @RequestBody QueueDueLoanInstallmentsRequest request) {
        int queued =
                recoveryService.queueDueInstallments(
                        request.tenantId(), request.companyId(), request.employeeIds());
        return Map.of("queued", queued);
    }
}
