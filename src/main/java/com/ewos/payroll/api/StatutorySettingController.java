package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateStatutorySettingRequest;
import com.ewos.payroll.api.dto.StatutorySettingResponse;
import com.ewos.payroll.application.StatutorySettingService;
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
@RequestMapping("/api/v1/payroll/statutory-settings")
@Tag(
        name = "Statutory Settings",
        description = "Jurisdiction-scoped, effective-dated compliance rates/values")
public class StatutorySettingController {

    private final StatutorySettingService service;

    public StatutorySettingController(StatutorySettingService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Create a statutory setting")
    public ResponseEntity<StatutorySettingResponse> create(
            @Valid @RequestBody CreateStatutorySettingRequest request) {
        StatutorySettingResponse created = service.create(request);
        return ResponseEntity.created(
                        URI.create("/api/v1/payroll/statutory-settings/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID")
    public StatutorySettingResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/jurisdiction/{jurisdiction}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List all statutory settings for a jurisdiction")
    public List<StatutorySettingResponse> listByJurisdiction(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable String jurisdiction) {
        return service.listByJurisdiction(tenantId, jurisdiction);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Soft-delete a statutory setting")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
