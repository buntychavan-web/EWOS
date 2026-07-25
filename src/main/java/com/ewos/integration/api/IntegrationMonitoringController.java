package com.ewos.integration.api;

import com.ewos.integration.api.dto.IntegrationMonitoringSummaryResponse;
import com.ewos.integration.application.IntegrationMonitoringService;
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
@RequestMapping("/api/v1/integration/monitoring")
@Tag(
        name = "Integration Monitoring",
        description =
                "Sprint 14.4 — success/failure counts and error-classification breakdown built"
                        + " from the integration execution audit trail.")
public class IntegrationMonitoringController {

    private final IntegrationMonitoringService service;

    public IntegrationMonitoringController(IntegrationMonitoringService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @Operation(summary = "Integration execution summary for a company")
    public IntegrationMonitoringSummaryResponse summary(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return service.summaryForCompany(tenantId, companyId);
    }
}
