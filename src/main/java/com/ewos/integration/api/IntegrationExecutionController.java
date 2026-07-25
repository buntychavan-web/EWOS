package com.ewos.integration.api;

import com.ewos.integration.api.dto.IntegrationExecutionResponse;
import com.ewos.integration.application.IntegrationExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integration/executions")
@Tag(
        name = "Integration Executions",
        description =
                "Sprint 14.4 — runs the company's configured adapter against a Data Exchange"
                        + " record and drives its status forward. Append-only execution history"
                        + " doubles as the enhanced operational audit trail.")
public class IntegrationExecutionController {

    private final IntegrationExecutionService service;

    public IntegrationExecutionController(IntegrationExecutionService service) {
        this.service = service;
    }

    @PostMapping("/process/{dataExchangeRecordId}")
    @PreAuthorize("hasAuthority('INTEGRATION_WRITE')")
    @Operation(summary = "Execute the configured adapter for a Data Exchange record")
    public IntegrationExecutionResponse process(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID dataExchangeRecordId) {
        return service.process(tenantId, dataExchangeRecordId);
    }

    @GetMapping("/of-record/{dataExchangeRecordId}")
    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @Operation(summary = "Execution attempt history for a Data Exchange record")
    public List<IntegrationExecutionResponse> historyOf(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID dataExchangeRecordId) {
        return service.historyOf(tenantId, dataExchangeRecordId);
    }
}
