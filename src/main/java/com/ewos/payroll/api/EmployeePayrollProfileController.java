package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateEmployeePayrollProfileRequest;
import com.ewos.payroll.api.dto.EmployeePayrollProfileResponse;
import com.ewos.payroll.application.EmployeePayrollProfileService;
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
@RequestMapping("/api/v1/payroll/profiles")
@Tag(
        name = "Employee Payroll Profiles",
        description = "Per-employee payroll assignment and statutory IDs")
public class EmployeePayrollProfileController {

    private final EmployeePayrollProfileService service;

    public EmployeePayrollProfileController(EmployeePayrollProfileService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Create a new profile; supersedes any previously active record")
    public ResponseEntity<EmployeePayrollProfileResponse> create(
            @Valid @RequestBody CreateEmployeePayrollProfileRequest request) {
        EmployeePayrollProfileResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/profiles/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID")
    public EmployeePayrollProfileResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/employee/{employeeId}/active")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Currently active profile for an employee")
    public EmployeePayrollProfileResponse activeForEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.activeForEmployee(tenantId, employeeId);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Profile history for an employee")
    public List<EmployeePayrollProfileResponse> historyForEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.historyForEmployee(tenantId, employeeId);
    }

    @GetMapping("/paygroup/{payGroupId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "All active profiles in a pay group")
    public List<EmployeePayrollProfileResponse> forPayGroup(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID payGroupId) {
        return service.forPayGroup(tenantId, payGroupId);
    }
}
