package com.ewos.integration.api;

import com.ewos.integration.api.dto.CreateIntegrationConfigurationRequest;
import com.ewos.integration.api.dto.IntegrationConfigurationResponse;
import com.ewos.integration.api.dto.UpdateIntegrationConfigurationRequest;
import com.ewos.integration.application.IntegrationConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@RequestMapping("/api/v1/integration/configurations")
@Tag(
        name = "Integration Configurations",
        description =
                "Sprint 14.4 — which adapter (REST/SFTP/CSV/EXCEL/FILE_UPLOAD) and connection"
                        + " settings a company uses per Data Exchange exchangeType.")
public class IntegrationConfigurationController {

    private final IntegrationConfigurationService service;

    public IntegrationConfigurationController(IntegrationConfigurationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INTEGRATION_WRITE')")
    @Operation(summary = "Create an integration configuration for a company + exchange type")
    @ApiResponse(
            responseCode = "201",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    IntegrationConfigurationResponse.class)))
    public ResponseEntity<IntegrationConfigurationResponse> create(
            @Valid @RequestBody CreateIntegrationConfigurationRequest request) {
        IntegrationConfigurationResponse created = service.create(request);
        return ResponseEntity.created(
                        URI.create("/api/v1/integration/configurations/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @Operation(summary = "Fetch an integration configuration by ID")
    public IntegrationConfigurationResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @Operation(summary = "List configurations for a company")
    public List<IntegrationConfigurationResponse> forCompany(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return service.forCompany(tenantId, companyId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('INTEGRATION_WRITE')")
    @Operation(summary = "Update connection settings or active flag")
    public IntegrationConfigurationResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIntegrationConfigurationRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INTEGRATION_WRITE')")
    @Operation(summary = "Remove an integration configuration")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
