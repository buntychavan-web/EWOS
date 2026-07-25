package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.reports.ProviderDashboardResponse;
import com.ewos.payroll.application.ProviderDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/provider-dashboard")
@Tag(
        name = "Provider Dashboard",
        description =
                "Sprint 14.2 — the Payroll Service Provider's book of business: assigned clients,"
                        + " active payroll periods, payroll run status, pending approvals, payroll"
                        + " calendar, and service catalogue status. Scoped entirely to the caller's"
                        + " own Chinese Wall client assignments.")
public class ProviderDashboardController {

    private final ProviderDashboardService service;

    public ProviderDashboardController(ProviderDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Provider dashboard: clients, periods, run status, and calendar")
    public ProviderDashboardResponse get(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.getDashboard(tenantId);
    }
}
