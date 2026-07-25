package com.ewos.integration.api;

import com.ewos.integration.api.dto.OperationsDashboardResponse;
import com.ewos.integration.application.OperationsDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integration/operations-dashboard")
@Tag(
        name = "Operations Dashboard",
        description =
                "Sprint 14.4 — one row per Payroll Run showing its status across the full"
                        + " pipeline: Payroll -> Client Approval -> Data Exchange -> Integration ->"
                        + " Acknowledgement.")
public class OperationsDashboardController {

    private final OperationsDashboardService service;

    public OperationsDashboardController(OperationsDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @Operation(summary = "Full pipeline status for a company's recent payroll runs")
    public OperationsDashboardResponse forCompany(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return service.forCompany(tenantId, companyId);
    }
}
