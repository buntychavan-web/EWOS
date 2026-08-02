package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateIncomeTaxSurchargeSlabRequest;
import com.ewos.payroll.api.dto.IncomeTaxSurchargeSlabResponse;
import com.ewos.payroll.api.dto.UpdateIncomeTaxSurchargeSlabRequest;
import com.ewos.payroll.application.IncomeTaxSurchargeSlabService;
import com.ewos.payroll.domain.TaxRegime;
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
@RequestMapping("/api/v1/payroll/statutory/income-tax-surcharge-slabs")
@Tag(
        name = "Statutory Engine - Income Tax Surcharge",
        description = "Surcharge brackets per regime/fiscal year")
public class IncomeTaxSurchargeSlabController {

    private final IncomeTaxSurchargeSlabService service;

    public IncomeTaxSurchargeSlabController(IncomeTaxSurchargeSlabService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Create a surcharge slab")
    public ResponseEntity<IncomeTaxSurchargeSlabResponse> create(
            @Valid @RequestBody CreateIncomeTaxSurchargeSlabRequest request) {
        IncomeTaxSurchargeSlabResponse created = service.create(request);
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/payroll/statutory/income-tax-surcharge-slabs/"
                                        + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID")
    public IncomeTaxSurchargeSlabResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List surcharge slabs for a regime and fiscal year")
    public List<IncomeTaxSurchargeSlabResponse> forRegimeAndYear(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam TaxRegime regime,
            @RequestParam String fiscalYear) {
        return service.forRegimeAndYear(tenantId, regime, fiscalYear);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Update mutable fields")
    public IncomeTaxSurchargeSlabResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIncomeTaxSurchargeSlabRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Delete a surcharge slab")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
