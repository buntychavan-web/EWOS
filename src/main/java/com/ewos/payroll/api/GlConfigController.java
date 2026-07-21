package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.BusinessUnitResponse;
import com.ewos.payroll.api.dto.CostCentreResponse;
import com.ewos.payroll.api.dto.CreateBusinessUnitRequest;
import com.ewos.payroll.api.dto.CreateCostCentreRequest;
import com.ewos.payroll.api.dto.CreateEmployeeCostAllocationRequest;
import com.ewos.payroll.api.dto.CreateGLAccountRequest;
import com.ewos.payroll.api.dto.CreateGLMappingRequest;
import com.ewos.payroll.api.dto.EmployeeCostAllocationResponse;
import com.ewos.payroll.api.dto.GLAccountResponse;
import com.ewos.payroll.api.dto.GLMappingResponse;
import com.ewos.payroll.application.EmployeeCostAllocationService;
import com.ewos.payroll.application.GlConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/payroll/gl")
@Tag(
        name = "Payroll GL Config",
        description = "Cost centres, business units, GL accounts, mappings, and cost allocations")
public class GlConfigController {

    private final GlConfigService gl;
    private final EmployeeCostAllocationService allocations;

    public GlConfigController(GlConfigService gl, EmployeeCostAllocationService allocations) {
        this.gl = gl;
        this.allocations = allocations;
    }

    // Cost centres
    @PostMapping("/cost-centres")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Create a cost centre")
    public CostCentreResponse createCostCentre(@Valid @RequestBody CreateCostCentreRequest r) {
        return gl.createCostCentre(r);
    }

    @GetMapping("/cost-centres")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List cost centres")
    public List<CostCentreResponse> listCostCentres(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @org.springframework.web.bind.annotation.RequestParam UUID companyId) {
        return gl.listCostCentres(tenantId, companyId);
    }

    @DeleteMapping("/cost-centres/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Soft-delete a cost centre")
    public ResponseEntity<Void> deleteCostCentre(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        gl.deleteCostCentre(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    // Business units
    @PostMapping("/business-units")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Create a business unit")
    public BusinessUnitResponse createBusinessUnit(
            @Valid @RequestBody CreateBusinessUnitRequest r) {
        return gl.createBusinessUnit(r);
    }

    @GetMapping("/business-units")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List business units")
    public List<BusinessUnitResponse> listBusinessUnits(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @org.springframework.web.bind.annotation.RequestParam UUID companyId) {
        return gl.listBusinessUnits(tenantId, companyId);
    }

    @DeleteMapping("/business-units/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Soft-delete a business unit")
    public ResponseEntity<Void> deleteBusinessUnit(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        gl.deleteBusinessUnit(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    // GL accounts
    @PostMapping("/accounts")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Create a GL account")
    public GLAccountResponse createAccount(@Valid @RequestBody CreateGLAccountRequest r) {
        return gl.createAccount(r);
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List GL accounts")
    public List<GLAccountResponse> listAccounts(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @org.springframework.web.bind.annotation.RequestParam UUID companyId) {
        return gl.listAccounts(tenantId, companyId);
    }

    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Soft-delete a GL account")
    public ResponseEntity<Void> deleteAccount(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        gl.deleteAccount(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    // GL mappings
    @PostMapping("/mappings")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Create a GL mapping")
    public GLMappingResponse createMapping(@Valid @RequestBody CreateGLMappingRequest r) {
        return gl.createMapping(r);
    }

    @GetMapping("/mappings")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List GL mappings")
    public List<GLMappingResponse> listMappings(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @org.springframework.web.bind.annotation.RequestParam UUID companyId) {
        return gl.listMappings(tenantId, companyId);
    }

    @DeleteMapping("/mappings/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Soft-delete a GL mapping")
    public ResponseEntity<Void> deleteMapping(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        gl.deleteMapping(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    // Employee cost allocations
    @PostMapping("/employee-allocations")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Create an employee cost allocation")
    public EmployeeCostAllocationResponse createAllocation(
            @Valid @RequestBody CreateEmployeeCostAllocationRequest r) {
        return allocations.create(r);
    }

    @GetMapping("/employee-allocations/employee/{employeeId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Active cost allocations for an employee")
    public List<EmployeeCostAllocationResponse> allocationsForEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return allocations.forEmployee(tenantId, employeeId);
    }

    @DeleteMapping("/employee-allocations/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Deactivate an employee cost allocation")
    public ResponseEntity<Void> deactivateAllocation(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        allocations.deactivate(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
