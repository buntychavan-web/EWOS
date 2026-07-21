package com.ewos.interview.api;

import com.ewos.interview.api.dto.CreateInterviewTemplateRequest;
import com.ewos.interview.api.dto.InterviewTemplateResponse;
import com.ewos.interview.api.dto.UpdateInterviewTemplateRequest;
import com.ewos.interview.application.InterviewTemplateService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interviews/templates")
@Tag(name = "Interview Templates", description = "Reusable interview-round configurations")
public class InterviewTemplateController {

    private final InterviewTemplateService templates;

    public InterviewTemplateController(InterviewTemplateService templates) {
        this.templates = templates;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Create an interview template")
    public InterviewTemplateResponse create(
            @Valid @RequestBody CreateInterviewTemplateRequest req) {
        return templates.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Update an interview template")
    public InterviewTemplateResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInterviewTemplateRequest req) {
        return templates.update(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INTERVIEW_READ')")
    @Operation(summary = "Fetch an interview template")
    public InterviewTemplateResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return templates.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INTERVIEW_READ')")
    @Operation(summary = "List interview templates for a company")
    public List<InterviewTemplateResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return templates.listForCompany(tenantId, companyId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INTERVIEW_ADMIN')")
    @Operation(summary = "Soft-delete an interview template")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        templates.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
