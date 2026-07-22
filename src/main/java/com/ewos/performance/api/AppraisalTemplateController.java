package com.ewos.performance.api;

import com.ewos.performance.api.dto.AppraisalTemplateResponse;
import com.ewos.performance.api.dto.CreateAppraisalTemplateRequest;
import com.ewos.performance.api.dto.CreateTemplateSectionRequest;
import com.ewos.performance.api.dto.TemplateSectionResponse;
import com.ewos.performance.api.dto.UpdateAppraisalTemplateRequest;
import com.ewos.performance.application.AppraisalTemplateService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appraisal-templates")
@Tag(name = "Appraisal Templates", description = "Reusable appraisal templates + sections")
public class AppraisalTemplateController {

    private final AppraisalTemplateService templates;

    public AppraisalTemplateController(AppraisalTemplateService templates) {
        this.templates = templates;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERF_ADMIN')")
    @Operation(summary = "Create an appraisal template")
    public ResponseEntity<AppraisalTemplateResponse> create(
            @Valid @RequestBody CreateAppraisalTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templates.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERF_ADMIN')")
    @Operation(summary = "Update an appraisal template")
    public AppraisalTemplateResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAppraisalTemplateRequest req) {
        return templates.update(tenantId, id, req);
    }

    @PostMapping("/{id}/sections")
    @PreAuthorize("hasAuthority('PERF_ADMIN')")
    @Operation(summary = "Add a weighted section to a template")
    public ResponseEntity<TemplateSectionResponse> addSection(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody CreateTemplateSectionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templates.addSection(tenantId, id, req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Fetch an appraisal template by id")
    public AppraisalTemplateResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return templates.getById(tenantId, id);
    }

    @GetMapping("/{id}/sections")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "List a template's sections ordered by display order")
    public List<TemplateSectionResponse> sections(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return templates.listSections(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "List active appraisal templates for a company")
    public List<AppraisalTemplateResponse> listActive(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return templates.listActive(tenantId, companyId);
    }
}
