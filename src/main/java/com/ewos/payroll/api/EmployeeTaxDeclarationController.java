package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateEmployeeTaxDeclarationRequest;
import com.ewos.payroll.api.dto.EmployeeTaxDeclarationResponse;
import com.ewos.payroll.api.dto.UpdateEmployeeTaxDeclarationRequest;
import com.ewos.payroll.application.EmployeeTaxDeclarationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/employee-tax-declarations")
@Tag(name = "Employee Tax Declarations", description = "Per-employee, per-fiscal-year TDS inputs")
public class EmployeeTaxDeclarationController {

    private final EmployeeTaxDeclarationService service;

    public EmployeeTaxDeclarationController(EmployeeTaxDeclarationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Create an employee's tax declaration for a fiscal year")
    public ResponseEntity<EmployeeTaxDeclarationResponse> create(
            @Valid @RequestBody CreateEmployeeTaxDeclarationRequest request) {
        EmployeeTaxDeclarationResponse created = service.create(request);
        return ResponseEntity.created(
                        URI.create("/api/v1/payroll/employee-tax-declarations/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID")
    public EmployeeTaxDeclarationResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch an employee's declaration for a specific fiscal year")
    public EmployeeTaxDeclarationResponse forEmployeeAndYear(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID employeeId,
            @RequestParam String fiscalYear) {
        return service.forEmployeeAndYear(tenantId, employeeId, fiscalYear);
    }

    @GetMapping("/employee/{employeeId}/history")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List all fiscal-year declarations for an employee")
    public List<EmployeeTaxDeclarationResponse> historyForEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.historyForEmployee(tenantId, employeeId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Update mutable fields")
    public EmployeeTaxDeclarationResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeTaxDeclarationRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Delete a tax declaration")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
