package com.ewos.integration.api;

import com.ewos.integration.api.dto.ClientGoLiveConfigurationResponse;
import com.ewos.integration.api.dto.CreateClientGoLiveConfigurationRequest;
import com.ewos.integration.api.dto.UpdateClientGoLiveConfigurationRequest;
import com.ewos.integration.application.ClientGoLiveConfigurationService;
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
@RequestMapping("/api/v1/integration/golive")
@Tag(
        name = "Client Go-Live Configuration",
        description = "Sprint 14.4 — readiness of an outsourced payroll engagement to go live.")
public class ClientGoLiveConfigurationController {

    private final ClientGoLiveConfigurationService service;

    public ClientGoLiveConfigurationController(ClientGoLiveConfigurationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GOLIVE_WRITE')")
    @Operation(summary = "Create a go-live configuration for a company")
    @ApiResponse(
            responseCode = "201",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    ClientGoLiveConfigurationResponse.class)))
    public ResponseEntity<ClientGoLiveConfigurationResponse> create(
            @Valid @RequestBody CreateClientGoLiveConfigurationRequest request) {
        ClientGoLiveConfigurationResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/integration/golive/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GOLIVE_READ')")
    @Operation(summary = "Fetch a go-live configuration by ID")
    public ClientGoLiveConfigurationResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/by-company")
    @PreAuthorize("hasAuthority('GOLIVE_READ')")
    @Operation(summary = "Fetch the go-live configuration for a company")
    public ClientGoLiveConfigurationResponse forCompany(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return service.forCompany(tenantId, companyId);
    }

    @GetMapping("/by-client")
    @PreAuthorize("hasAuthority('GOLIVE_READ')")
    @Operation(summary = "List go-live configurations for a client's companies")
    public List<ClientGoLiveConfigurationResponse> forClient(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID clientId) {
        return service.forClient(tenantId, clientId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('GOLIVE_WRITE')")
    @Operation(summary = "Update go-live date, status, or notes")
    public ClientGoLiveConfigurationResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientGoLiveConfigurationRequest request) {
        return service.update(tenantId, id, request);
    }
}
