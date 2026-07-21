package com.ewos.recruitment.api;

import com.ewos.recruitment.api.dto.CreateJobPositionRequest;
import com.ewos.recruitment.api.dto.JobPositionResponse;
import com.ewos.recruitment.api.dto.UpdateJobPositionRequest;
import com.ewos.recruitment.application.JobPositionService;
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
@RequestMapping("/api/v1/recruitment/positions")
@Tag(name = "Recruitment Positions", description = "Job positions catalogue (long-lived seats)")
public class JobPositionController {

    private final JobPositionService positions;

    public JobPositionController(JobPositionService positions) {
        this.positions = positions;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RECRUITMENT_WRITE')")
    @Operation(summary = "Create a job position")
    public JobPositionResponse create(@Valid @RequestBody CreateJobPositionRequest req) {
        return positions.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('RECRUITMENT_WRITE')")
    @Operation(summary = "Update a job position")
    public JobPositionResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateJobPositionRequest req) {
        return positions.update(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RECRUITMENT_READ')")
    @Operation(summary = "Fetch a job position by ID")
    public JobPositionResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return positions.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RECRUITMENT_READ')")
    @Operation(summary = "List job positions for a company")
    public List<JobPositionResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return positions.listForCompany(tenantId, companyId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('RECRUITMENT_ADMIN')")
    @Operation(summary = "Soft-delete a job position")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        positions.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
