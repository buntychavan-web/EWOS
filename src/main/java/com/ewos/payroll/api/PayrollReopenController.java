package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.PayrollRunReopenAuthorizationResponse;
import com.ewos.payroll.api.dto.ReopenPayrollRunRequest;
import com.ewos.payroll.application.PayrollReopenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Sprint 24L item 2 — authorized reopen of a FROZEN payroll run for correction. */
@RestController
@Tag(name = "Payroll Reopen", description = "Authorized reopen of a FROZEN payroll run")
public class PayrollReopenController {

    private final PayrollReopenService service;

    public PayrollReopenController(PayrollReopenService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/payroll/runs/{id}/reopen")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(
            summary =
                    "Authorize exactly one correction supplementary run against a FROZEN run's"
                            + " period, with a mandatory reason")
    public PayrollRunReopenAuthorizationResponse authorizeReopen(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ReopenPayrollRunRequest request) {
        return service.authorizeReopen(tenantId, id, request);
    }

    @PostMapping("/api/v1/payroll/reopen-authorizations/{authorizationId}/revoke")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Revoke an ACTIVE reopen authorization before it is used")
    public PayrollRunReopenAuthorizationResponse revoke(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID authorizationId) {
        return service.revoke(tenantId, authorizationId);
    }

    @GetMapping("/api/v1/payroll/runs/{id}/reopen-history")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Complete audit history of reopen authorizations for this run")
    public List<PayrollRunReopenAuthorizationResponse> historyForRun(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.historyForRun(tenantId, id);
    }
}
