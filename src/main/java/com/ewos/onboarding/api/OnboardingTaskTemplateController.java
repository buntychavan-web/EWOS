package com.ewos.onboarding.api;

import com.ewos.onboarding.api.dto.CreateOnboardingTaskTemplateRequest;
import com.ewos.onboarding.api.dto.OnboardingTaskTemplateResponse;
import com.ewos.onboarding.application.OnboardingTaskTemplateService;
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
@RequestMapping("/api/v1/onboarding/task-templates")
@Tag(name = "Onboarding Task Templates", description = "Reusable post-joining task templates")
public class OnboardingTaskTemplateController {

    private final OnboardingTaskTemplateService templates;

    public OnboardingTaskTemplateController(OnboardingTaskTemplateService templates) {
        this.templates = templates;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ONBOARDING_ADMIN')")
    @Operation(summary = "Create an onboarding task template")
    public OnboardingTaskTemplateResponse create(
            @Valid @RequestBody CreateOnboardingTaskTemplateRequest req) {
        return templates.create(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    @Operation(summary = "List task templates for a company")
    public List<OnboardingTaskTemplateResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return templates.listForCompany(tenantId, companyId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ONBOARDING_ADMIN')")
    @Operation(summary = "Soft-delete a task template")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        templates.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
