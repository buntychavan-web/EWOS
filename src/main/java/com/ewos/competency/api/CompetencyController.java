package com.ewos.competency.api;

import com.ewos.competency.api.dto.CompetencyResponse;
import com.ewos.competency.api.dto.CreateCompetencyRequest;
import com.ewos.competency.api.dto.UpdateCompetencyRequest;
import com.ewos.competency.application.CompetencyService;
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
@RequestMapping("/api/v1/competencies")
@Tag(name = "Competencies", description = "Reusable competency library")
public class CompetencyController {

    private final CompetencyService competencies;

    public CompetencyController(CompetencyService competencies) {
        this.competencies = competencies;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COMPETENCY_ADMIN')")
    @Operation(summary = "Create a competency")
    public ResponseEntity<CompetencyResponse> create(
            @Valid @RequestBody CreateCompetencyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(competencies.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPETENCY_ADMIN')")
    @Operation(summary = "Update a competency")
    public CompetencyResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompetencyRequest req) {
        return competencies.update(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "Fetch a competency by id")
    public CompetencyResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return competencies.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "List active competencies for a company")
    public List<CompetencyResponse> listActive(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return competencies.listActive(tenantId, companyId);
    }
}
