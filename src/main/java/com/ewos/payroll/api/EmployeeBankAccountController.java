package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateEmployeeBankAccountRequest;
import com.ewos.payroll.api.dto.EmployeeBankAccountResponse;
import com.ewos.payroll.application.EmployeeBankAccountService;
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
@RequestMapping("/api/v1/payroll/bank-accounts")
@Tag(name = "Employee Bank Accounts", description = "Salary-credit bank details")
public class EmployeeBankAccountController {

    private final EmployeeBankAccountService service;

    public EmployeeBankAccountController(EmployeeBankAccountService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Add a bank account; if primary, demotes the existing primary")
    public ResponseEntity<EmployeeBankAccountResponse> create(
            @Valid @RequestBody CreateEmployeeBankAccountRequest request) {
        EmployeeBankAccountResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/bank-accounts/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/primary")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Mark this account as the employee's primary")
    public EmployeeBankAccountResponse setPrimary(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.setPrimary(tenantId, id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Deactivate a bank account")
    public ResponseEntity<Void> deactivate(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.deactivate(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID (account number returned masked)")
    public EmployeeBankAccountResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "All bank accounts for an employee (primary first)")
    public List<EmployeeBankAccountResponse> forEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.forEmployee(tenantId, employeeId);
    }
}
