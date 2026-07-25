package com.ewos.tenancy.api;

import com.ewos.shared.exception.ApiError;
import com.ewos.tenancy.api.dto.ClientResponse;
import com.ewos.tenancy.api.dto.CreateClientRequest;
import com.ewos.tenancy.api.dto.UpdateClientRequest;
import com.ewos.tenancy.application.ClientService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clients")
@Tag(
        name = "Clients",
        description =
                "Commercial customer relationships within a tenant. Reads/writes for a specific"
                        + " client are Chinese-Wall scoped — see ClientAccessGuard.")
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLIENT_WRITE')")
    @Operation(summary = "Create a new client")
    @ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = ClientResponse.class)))
    @ApiResponse(
            responseCode = "409",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody CreateClientRequest request) {
        ClientResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/clients/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    @Operation(summary = "Fetch a client by ID within the tenant (Chinese-Wall scoped)")
    @ApiResponse(
            responseCode = "403",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ClientResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    @Operation(
            summary = "List clients for the tenant",
            description =
                    "Filtered to the caller's assigned clients unless they hold CLIENT_ADMIN.")
    public List<ClientResponse> list(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.list(tenantId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENT_WRITE')")
    @Operation(summary = "Update mutable fields on a client (Chinese-Wall scoped)")
    public ClientResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENT_ADMIN')")
    @Operation(summary = "Soft-delete a client; fails 409 if it still has companies")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
