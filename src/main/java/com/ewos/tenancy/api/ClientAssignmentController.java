package com.ewos.tenancy.api;

import com.ewos.shared.exception.ApiError;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.dto.ClientAssignmentResponse;
import com.ewos.tenancy.api.dto.CreateClientAssignmentRequest;
import com.ewos.tenancy.api.dto.UpdateClientAssignmentRequest;
import com.ewos.tenancy.application.ClientAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/client-assignments")
@Tag(
        name = "Client Assignments",
        description =
                "Which provider-staff user may work on which client, optionally narrowed to one"
                        + " service — the Chinese Wall enforcement table. Gated purely by"
                        + " CLIENT_ASSIGNMENT_* — granting/revoking access is itself the admin"
                        + " function.")
public class ClientAssignmentController {

    private final ClientAssignmentService service;

    public ClientAssignmentController(ClientAssignmentService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLIENT_ASSIGNMENT_WRITE')")
    @Operation(summary = "Grant a provider-staff user access to a client")
    @ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = ClientAssignmentResponse.class)))
    public ResponseEntity<ClientAssignmentResponse> create(
            @Valid @RequestBody CreateClientAssignmentRequest request) {
        ClientAssignmentResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/client-assignments/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENT_ASSIGNMENT_READ')")
    @Operation(summary = "Fetch a client assignment by ID")
    public ClientAssignmentResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENT_ASSIGNMENT_READ')")
    @Operation(
            summary = "List assignments, filtered by clientId or providerId (exactly one required)")
    @ApiResponse(
            responseCode = "400",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public List<ClientAssignmentResponse> list(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID providerId) {
        if (clientId != null) {
            return service.listByClient(clientId);
        }
        if (providerId != null) {
            return service.listByProvider(providerId);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Either clientId or providerId is required");
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENT_ASSIGNMENT_WRITE')")
    @Operation(summary = "Update scope role, active flag, or effective-to date")
    public ClientAssignmentResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdateClientAssignmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENT_ASSIGNMENT_WRITE')")
    @Operation(summary = "Revoke a client assignment — takes effect on the user's next request")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        service.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
