package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateEsiConfigurationRequest;
import com.ewos.payroll.api.dto.EsiConfigurationResponse;
import com.ewos.payroll.api.dto.UpdateEsiConfigurationRequest;
import com.ewos.payroll.application.EsiConfigurationService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/statutory/esi-configurations")
@Tag(name = "Statutory Engine - ESI", description = "Employees' State Insurance configuration")
public class EsiConfigurationController {

    private final EsiConfigurationService service;

    public EsiConfigurationController(EsiConfigurationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Create an ESI configuration")
    public ResponseEntity<EsiConfigurationResponse> create(
            @Valid @RequestBody CreateEsiConfigurationRequest request) {
        EsiConfigurationResponse created = service.create(request);
        return ResponseEntity.created(
                        URI.create("/api/v1/payroll/statutory/esi-configurations/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID")
    public EsiConfigurationResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List tenant-wide default plus company-specific ESI configurations")
    public List<EsiConfigurationResponse> forCompany(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID companyId) {
        return service.forCompany(tenantId, companyId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Update mutable fields")
    public EsiConfigurationResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEsiConfigurationRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Delete an ESI configuration")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
