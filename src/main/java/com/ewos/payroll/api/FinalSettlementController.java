package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateFinalSettlementRequest;
import com.ewos.payroll.api.dto.FinalSettlementResponse;
import com.ewos.payroll.api.dto.UpdateFinalSettlementRequest;
import com.ewos.payroll.application.FinalSettlementService;
import com.ewos.payroll.domain.FinalSettlementStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/payroll/settlements")
@Tag(
        name = "Final Settlements",
        description = "Full & Final settlement lifecycle for terminated employees")
public class FinalSettlementController {

    private final FinalSettlementService service;

    public FinalSettlementController(FinalSettlementService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_WRITE')")
    @Operation(summary = "Create a draft F&F settlement")
    public ResponseEntity<FinalSettlementResponse> create(
            @Valid @RequestBody CreateFinalSettlementRequest request) {
        FinalSettlementResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/settlements/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_WRITE')")
    @Operation(summary = "Edit a DRAFT settlement")
    public FinalSettlementResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFinalSettlementRequest request) {
        return service.update(tenantId, id, request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Approve a DRAFT settlement; queues arrears for the settlement run")
    public FinalSettlementResponse approve(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.approve(tenantId, id);
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    @Operation(summary = "Settle an APPROVED F&F; starts a FINAL_SETTLEMENT payroll run")
    public FinalSettlementResponse settle(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam UUID payrollPeriodId) {
        return service.settle(tenantId, id, payrollPeriodId);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PAYROLL_WRITE')")
    @Operation(summary = "Cancel a non-SETTLED settlement")
    public FinalSettlementResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.cancel(tenantId, id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID")
    public FinalSettlementResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List by status for a company")
    public List<FinalSettlementResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam FinalSettlementStatus status) {
        return service.byStatus(tenantId, companyId, status);
    }
}
