package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateEmployeeCompensationRequest;
import com.ewos.payroll.api.dto.EmployeeCompensationResponse;
import com.ewos.payroll.application.EmployeeCompensationService;
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
@RequestMapping("/api/v1/payroll/compensations")
@Tag(
        name = "Employee Compensations",
        description = "Effective-dated salary structures per employee")
public class EmployeeCompensationController {

    private final EmployeeCompensationService service;

    public EmployeeCompensationController(EmployeeCompensationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_WRITE')")
    @Operation(summary = "Create a new compensation record; supersedes any active record")
    public ResponseEntity<EmployeeCompensationResponse> create(
            @Valid @RequestBody CreateEmployeeCompensationRequest request) {
        EmployeeCompensationResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/compensations/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID")
    public EmployeeCompensationResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Full compensation history for an employee (newest first)")
    public List<EmployeeCompensationResponse> historyForEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.historyForEmployee(tenantId, employeeId);
    }
}
