package com.ewos.probation.api;

import com.ewos.probation.api.dto.CreateProbationPolicyRequest;
import com.ewos.probation.api.dto.ProbationPolicyResponse;
import com.ewos.probation.api.dto.UpdateProbationPolicyRequest;
import com.ewos.probation.application.ProbationPolicyService;
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
@RequestMapping("/api/v1/probation-policies")
@Tag(name = "Probation Policies", description = "Per-company probation policy definitions")
public class ProbationPolicyController {

    private final ProbationPolicyService policies;

    public ProbationPolicyController(ProbationPolicyService policies) {
        this.policies = policies;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROBATION_ADMIN')")
    @Operation(summary = "Create a probation policy")
    public ResponseEntity<ProbationPolicyResponse> create(
            @Valid @RequestBody CreateProbationPolicyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(policies.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROBATION_ADMIN')")
    @Operation(summary = "Update an existing probation policy")
    public ProbationPolicyResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProbationPolicyRequest req) {
        return policies.update(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROBATION_READ')")
    @Operation(summary = "Fetch a probation policy by id")
    public ProbationPolicyResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return policies.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROBATION_READ')")
    @Operation(summary = "List active probation policies for a company")
    public List<ProbationPolicyResponse> listActive(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return policies.listActive(tenantId, companyId);
    }
}
