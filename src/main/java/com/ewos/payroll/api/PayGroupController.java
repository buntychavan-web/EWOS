package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreatePayGroupRequest;
import com.ewos.payroll.api.dto.PayGroupResponse;
import com.ewos.payroll.api.dto.UpdatePayGroupRequest;
import com.ewos.payroll.application.PayGroupService;
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
@RequestMapping("/api/v1/payroll/paygroups")
@Tag(name = "Pay Groups", description = "Per-company employee cohorts that share payroll cadence")
public class PayGroupController {

    private final PayGroupService service;

    public PayGroupController(PayGroupService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Create a new pay group")
    public ResponseEntity<PayGroupResponse> create(
            @Valid @RequestBody CreatePayGroupRequest request) {
        PayGroupResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/paygroups/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID (Redis-cached)")
    public PayGroupResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List all pay groups for a company")
    public List<PayGroupResponse> forCompany(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID companyId) {
        return service.forCompany(tenantId, companyId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Update mutable fields")
    public PayGroupResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePayGroupRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Soft-delete a pay group")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
