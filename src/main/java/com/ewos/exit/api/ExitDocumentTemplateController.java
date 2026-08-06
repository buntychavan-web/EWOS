package com.ewos.exit.api;

import com.ewos.exit.api.dto.CreateExitDocumentTemplateRequest;
import com.ewos.exit.api.dto.ExitDocumentTemplateResponse;
import com.ewos.exit.application.ExitDocumentTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exit/document-templates")
@Tag(
        name = "Exit — Document templates",
        description = "Configurable exit letter wording per document type")
public class ExitDocumentTemplateController {

    private final ExitDocumentTemplateService service;

    public ExitDocumentTemplateController(ExitDocumentTemplateService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXIT_ADMIN')")
    @Operation(summary = "Create an exit document template")
    public ResponseEntity<ExitDocumentTemplateResponse> create(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateExitDocumentTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(tenantId, req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXIT_ADMIN')")
    @Operation(summary = "Fetch an exit document template")
    public ExitDocumentTemplateResponse get(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXIT_ADMIN')")
    @Operation(summary = "List exit document templates for a company")
    public List<ExitDocumentTemplateResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return service.listForCompany(tenantId, companyId);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('EXIT_ADMIN')")
    @Operation(summary = "Activate an exit document template")
    public ExitDocumentTemplateResponse activate(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.setActive(tenantId, id, true);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('EXIT_ADMIN')")
    @Operation(summary = "Deactivate an exit document template")
    public ExitDocumentTemplateResponse deactivate(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.setActive(tenantId, id, false);
    }
}
