package com.ewos.tenancy.api;

import com.ewos.shared.exception.ApiError;
import com.ewos.tenancy.api.dto.CompanyResponse;
import com.ewos.tenancy.api.dto.CreateCompanyRequest;
import com.ewos.tenancy.api.dto.UpdateCompanyRequest;
import com.ewos.tenancy.application.CompanyService;
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
@RequestMapping("/api/v1/companies")
@Tag(
        name = "Companies",
        description =
                "Legal entities under a Client — one Client, multiple Companies. GET (list) also"
                        + " backs the frontend Company Switcher.")
public class CompanyController {

    private final CompanyService service;

    public CompanyController(CompanyService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Create a new company under a client")
    @ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = CompanyResponse.class)))
    @ApiResponse(
            responseCode = "409",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<CompanyResponse> create(
            @Valid @RequestBody CreateCompanyRequest request) {
        CompanyResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/companies/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @Operation(summary = "Fetch a company by ID within the tenant (Chinese-Wall scoped)")
    @ApiResponse(
            responseCode = "403",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CompanyResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @Operation(
            summary = "List companies for the tenant",
            description =
                    "Filtered to companies under the caller's assigned clients unless they hold"
                            + " CLIENT_ADMIN. This is what the Company Switcher calls.")
    public List<CompanyResponse> list(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.list(tenantId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Update mutable fields on a company (Chinese-Wall scoped)")
    public CompanyResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPANY_ADMIN')")
    @Operation(summary = "Soft-delete a company")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
