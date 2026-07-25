package com.ewos.tenancy.api;

import com.ewos.shared.exception.ApiError;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.dto.CreatePayrollCollaborationRequest;
import com.ewos.tenancy.api.dto.PayrollCollaborationResponse;
import com.ewos.tenancy.api.dto.UpdatePayrollCollaborationRequest;
import com.ewos.tenancy.application.PayrollCollaborationService;
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
@RequestMapping("/api/v1/payroll-collaborations")
@Tag(
        name = "Payroll Collaborations",
        description =
                "The engagement record between a Client and a Payroll Service Provider — scope"
                        + " (FULL/STATUTORY_ONLY/REVIEW_ONLY), status, and SLA. Gated by"
                        + " PAYROLL_COLLABORATION_* plus the Chinese Wall client-assignment check on"
                        + " every client-scoped operation.")
public class PayrollCollaborationController {

    private final PayrollCollaborationService service;

    public PayrollCollaborationController(PayrollCollaborationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_COLLABORATION_WRITE')")
    @Operation(summary = "Establish a payroll collaboration between a client and a provider")
    @ApiResponse(
            responseCode = "201",
            content =
                    @Content(schema = @Schema(implementation = PayrollCollaborationResponse.class)))
    public ResponseEntity<PayrollCollaborationResponse> create(
            @Valid @RequestBody CreatePayrollCollaborationRequest request) {
        PayrollCollaborationResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll-collaborations/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_COLLABORATION_READ')")
    @Operation(summary = "Fetch a payroll collaboration by ID")
    public PayrollCollaborationResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYROLL_COLLABORATION_READ')")
    @Operation(
            summary =
                    "List payroll collaborations, filtered by clientId or providerId (exactly one required)")
    @ApiResponse(
            responseCode = "400",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public List<PayrollCollaborationResponse> list(
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
    @PreAuthorize("hasAuthority('PAYROLL_COLLABORATION_WRITE')")
    @Operation(summary = "Update scope, status, effective-to date, or SLA days")
    public PayrollCollaborationResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdatePayrollCollaborationRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_COLLABORATION_WRITE')")
    @Operation(summary = "Remove a payroll collaboration record")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
