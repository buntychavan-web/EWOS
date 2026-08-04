package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateLwfConfigurationRequest;
import com.ewos.payroll.api.dto.LwfConfigurationResponse;
import com.ewos.payroll.api.dto.UpdateLwfConfigurationRequest;
import com.ewos.payroll.application.LwfConfigurationService;
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
@RequestMapping("/api/v1/payroll/statutory/lwf-configurations")
@Tag(name = "Statutory Engine - LWF", description = "State-wise Labour Welfare Fund schemes")
public class LwfConfigurationController {

    private final LwfConfigurationService service;

    public LwfConfigurationController(LwfConfigurationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Create an LWF configuration")
    public ResponseEntity<LwfConfigurationResponse> create(
            @Valid @RequestBody CreateLwfConfigurationRequest request) {
        LwfConfigurationResponse created = service.create(request);
        return ResponseEntity.created(
                        URI.create("/api/v1/payroll/statutory/lwf-configurations/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID")
    public LwfConfigurationResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/jurisdiction/{jurisdictionId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List all LWF configurations for a jurisdiction")
    public List<LwfConfigurationResponse> forJurisdiction(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID jurisdictionId) {
        return service.forJurisdiction(tenantId, jurisdictionId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Update mutable fields")
    public LwfConfigurationResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLwfConfigurationRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Delete an LWF configuration")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
