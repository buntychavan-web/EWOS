package com.ewos.exit.api;

import com.ewos.exit.api.dto.CreateExitChecklistTemplateRequest;
import com.ewos.exit.api.dto.ExitChecklistTemplateResponse;
import com.ewos.exit.application.ExitChecklistTemplateService;
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
@RequestMapping("/api/v1/exit/checklist-templates")
@Tag(
        name = "Exit — Checklist templates",
        description = "Configurable per-company/org-unit exit clearance checklists")
public class ExitChecklistTemplateController {

    private final ExitChecklistTemplateService service;

    public ExitChecklistTemplateController(ExitChecklistTemplateService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXIT_ADMIN')")
    @Operation(summary = "Create an exit checklist template with its items")
    public ResponseEntity<ExitChecklistTemplateResponse> create(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateExitChecklistTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(tenantId, req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXIT_ADMIN')")
    @Operation(summary = "Fetch an exit checklist template")
    public ExitChecklistTemplateResponse get(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXIT_ADMIN')")
    @Operation(summary = "List exit checklist templates for a company")
    public List<ExitChecklistTemplateResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return service.listForCompany(tenantId, companyId);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('EXIT_ADMIN')")
    @Operation(summary = "Activate an exit checklist template")
    public ExitChecklistTemplateResponse activate(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.setActive(tenantId, id, true);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('EXIT_ADMIN')")
    @Operation(summary = "Deactivate an exit checklist template")
    public ExitChecklistTemplateResponse deactivate(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.setActive(tenantId, id, false);
    }
}
