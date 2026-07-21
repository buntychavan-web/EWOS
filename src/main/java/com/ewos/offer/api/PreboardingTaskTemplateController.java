package com.ewos.offer.api;

import com.ewos.offer.api.dto.CreatePreboardingTaskTemplateRequest;
import com.ewos.offer.api.dto.PreboardingTaskTemplateResponse;
import com.ewos.offer.application.PreboardingTaskTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preboarding/task-templates")
@Tag(name = "Preboarding Task Templates", description = "Reusable pre-joining task templates")
public class PreboardingTaskTemplateController {

    private final PreboardingTaskTemplateService templates;

    public PreboardingTaskTemplateController(PreboardingTaskTemplateService templates) {
        this.templates = templates;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PREBOARDING_ADMIN')")
    @Operation(summary = "Create a pre-boarding task template")
    public PreboardingTaskTemplateResponse create(
            @Valid @RequestBody CreatePreboardingTaskTemplateRequest req) {
        return templates.create(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PREBOARDING_READ')")
    @Operation(summary = "List task templates for a company")
    public List<PreboardingTaskTemplateResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return templates.listForCompany(tenantId, companyId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PREBOARDING_ADMIN')")
    @Operation(summary = "Soft-delete a pre-boarding task template")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        templates.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
