package com.ewos.tenancy.api;

import com.ewos.shared.exception.ApiError;
import com.ewos.tenancy.api.dto.CreatePayrollServiceProviderRequest;
import com.ewos.tenancy.api.dto.PayrollServiceProviderResponse;
import com.ewos.tenancy.api.dto.UpdatePayrollServiceProviderRequest;
import com.ewos.tenancy.application.PayrollServiceProviderService;
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
@RequestMapping("/api/v1/payroll-service-providers")
@Tag(
        name = "Payroll Service Providers",
        description = "The vendor organization operating the platform for its clients")
public class PayrollServiceProviderController {

    private final PayrollServiceProviderService service;

    public PayrollServiceProviderController(PayrollServiceProviderService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROVIDER_WRITE')")
    @Operation(summary = "Create a new payroll service provider")
    @ApiResponse(
            responseCode = "201",
            content =
                    @Content(
                            schema =
                                    @Schema(implementation = PayrollServiceProviderResponse.class)))
    @ApiResponse(
            responseCode = "409",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<PayrollServiceProviderResponse> create(
            @Valid @RequestBody CreatePayrollServiceProviderRequest request) {
        PayrollServiceProviderResponse created = service.create(request);
        return ResponseEntity.created(
                        URI.create("/api/v1/payroll-service-providers/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROVIDER_READ')")
    @Operation(summary = "Fetch a payroll service provider by ID within the tenant")
    public PayrollServiceProviderResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROVIDER_READ')")
    @Operation(summary = "List payroll service providers for the tenant")
    public List<PayrollServiceProviderResponse> list(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.list(tenantId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PROVIDER_WRITE')")
    @Operation(summary = "Update mutable fields on a payroll service provider")
    public PayrollServiceProviderResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePayrollServiceProviderRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PROVIDER_ADMIN')")
    @Operation(summary = "Soft-delete a payroll service provider")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
