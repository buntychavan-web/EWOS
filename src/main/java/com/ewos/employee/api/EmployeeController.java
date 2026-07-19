package com.ewos.employee.api;

import com.ewos.employee.api.dto.EmployeeResponse;
import com.ewos.employee.api.dto.EmployeeSearchCriteria;
import com.ewos.employee.api.dto.HireEmployeeRequest;
import com.ewos.employee.api.dto.TerminateEmployeeRequest;
import com.ewos.employee.api.dto.UpdateEmployeeRequest;
import com.ewos.employee.application.EmployeeService;
import com.ewos.employee.domain.EmployeeStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/v1/employees")
@Tag(name = "Employees", description = "Employee master; hire / update / terminate / search")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EMP_ADMIN')")
    @Operation(summary = "Hire a new employee")
    public ResponseEntity<EmployeeResponse> hire(@Valid @RequestBody HireEmployeeRequest request) {
        EmployeeResponse created = service.hire(request);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMP_READ')")
    @Operation(summary = "Fetch by ID (Redis-cached)")
    public EmployeeResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('EMP_WRITE')")
    @Operation(summary = "Update mutable fields (name, contact, manager, org-unit, ...)")
    public EmployeeResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return service.update(tenantId, id, request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('EMP_ADMIN')")
    @Operation(summary = "Change status (ACTIVE / ON_LEAVE / SUSPENDED)")
    public EmployeeResponse changeStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam("target") EmployeeStatus target) {
        return service.changeStatus(tenantId, id, target);
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAuthority('EMP_ADMIN')")
    @Operation(summary = "Terminate an employee (sets status TERMINATED + termination_date)")
    public EmployeeResponse terminate(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody TerminateEmployeeRequest request) {
        return service.terminate(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EMP_ADMIN')")
    @Operation(summary = "Soft-delete an employee; fails 409 if they have direct reports")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EMP_READ')")
    @Operation(summary = "Paged search")
    public Page<EmployeeResponse> search(
            @Valid EmployeeSearchCriteria criteria, Pageable pageable) {
        return service.search(criteria, pageable);
    }
}
