package com.ewos.succession.api;

import com.ewos.succession.api.dto.CareerPathResponse;
import com.ewos.succession.api.dto.CreateCareerPathRequest;
import com.ewos.succession.application.SuccessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/career-paths")
@Tag(name = "Career Paths", description = "Progression pathways between designations")
public class CareerPathController {

    private final SuccessionService succession;

    public CareerPathController(SuccessionService succession) {
        this.succession = succession;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUCCESSION_WRITE')")
    @Operation(summary = "Create a career path")
    public ResponseEntity<CareerPathResponse> create(
            @Valid @RequestBody CreateCareerPathRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(succession.createCareerPath(req));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SUCCESSION_READ')")
    @Operation(summary = "List active career paths")
    public List<CareerPathResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return succession.listCareerPaths(tenantId, companyId);
    }

    @GetMapping("/from")
    @PreAuthorize("hasAuthority('SUCCESSION_READ')")
    @Operation(summary = "List active career paths from a designation")
    public List<CareerPathResponse> fromDesignation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam String designation) {
        return succession.listCareerPathsFrom(tenantId, companyId, designation);
    }
}
