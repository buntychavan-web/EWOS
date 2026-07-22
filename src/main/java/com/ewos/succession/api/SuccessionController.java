package com.ewos.succession.api;

import com.ewos.succession.api.dto.AddCandidateRequest;
import com.ewos.succession.api.dto.AddMemberRequest;
import com.ewos.succession.api.dto.CandidateResponse;
import com.ewos.succession.api.dto.CreatePlanRequest;
import com.ewos.succession.api.dto.CreatePoolRequest;
import com.ewos.succession.api.dto.EligibilityRequest;
import com.ewos.succession.api.dto.EligibilityResponse;
import com.ewos.succession.api.dto.PlanResponse;
import com.ewos.succession.api.dto.PoolMemberResponse;
import com.ewos.succession.api.dto.PoolResponse;
import com.ewos.succession.api.dto.ReadinessRequest;
import com.ewos.succession.api.dto.ReadinessResponse;
import com.ewos.succession.api.dto.SuccessionDashboardResponse;
import com.ewos.succession.application.SuccessionService;
import com.ewos.succession.domain.TalentTier;
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
@RequestMapping("/api/v1/succession")
@Tag(
        name = "Succession",
        description = "Promotion eligibility, talent pools, successor plans, readiness")
public class SuccessionController {

    private final SuccessionService succession;

    public SuccessionController(SuccessionService succession) {
        this.succession = succession;
    }

    // Promotion eligibility ---------------------------------------------------

    @PostMapping("/eligibility")
    @PreAuthorize("hasAuthority('SUCCESSION_ASSESS')")
    @Operation(summary = "Record a promotion eligibility assessment")
    public ResponseEntity<EligibilityResponse> recordEligibility(
            @Valid @RequestBody EligibilityRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(succession.recordEligibility(req));
    }

    @GetMapping("/eligibility/{employeeId}")
    @PreAuthorize("hasAuthority('SUCCESSION_READ')")
    @Operation(summary = "List an employee's eligibility history")
    public List<EligibilityResponse> eligibilityHistory(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return succession.eligibilityHistory(tenantId, employeeId);
    }

    // Talent pools ------------------------------------------------------------

    @PostMapping("/pools")
    @PreAuthorize("hasAuthority('SUCCESSION_WRITE')")
    @Operation(summary = "Create a talent pool")
    public ResponseEntity<PoolResponse> createPool(@Valid @RequestBody CreatePoolRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(succession.createPool(req));
    }

    @GetMapping("/pools")
    @PreAuthorize("hasAuthority('SUCCESSION_READ')")
    @Operation(summary = "List active talent pools")
    public List<PoolResponse> listPools(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return succession.listPools(tenantId, companyId);
    }

    @PostMapping("/pools/{poolId}/members")
    @PreAuthorize("hasAuthority('SUCCESSION_WRITE')")
    @Operation(summary = "Add a member to a pool")
    public ResponseEntity<PoolMemberResponse> addMember(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID poolId,
            @Valid @RequestBody AddMemberRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(succession.addMember(tenantId, poolId, req));
    }

    @PostMapping("/pools/members/{memberId}/remove")
    @PreAuthorize("hasAuthority('SUCCESSION_WRITE')")
    @Operation(summary = "Remove a member from a pool")
    public PoolMemberResponse removeMember(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID memberId) {
        return succession.removeMember(tenantId, memberId);
    }

    @GetMapping("/pools/{poolId}/members")
    @PreAuthorize("hasAuthority('SUCCESSION_READ')")
    @Operation(summary = "List members of a pool")
    public List<PoolMemberResponse> members(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID poolId) {
        return succession.poolMembers(tenantId, poolId);
    }

    // Successor plans ---------------------------------------------------------

    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('SUCCESSION_WRITE')")
    @Operation(summary = "Create a successor plan for a key position")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(succession.createPlan(req));
    }

    @PostMapping("/plans/{planId}/candidates")
    @PreAuthorize("hasAuthority('SUCCESSION_WRITE')")
    @Operation(summary = "Add a candidate to a successor plan")
    public ResponseEntity<CandidateResponse> addCandidate(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID planId,
            @Valid @RequestBody AddCandidateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(succession.addCandidate(tenantId, planId, req));
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('SUCCESSION_READ')")
    @Operation(summary = "Fetch a successor plan by id")
    public PlanResponse getPlan(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return succession.getPlan(tenantId, id);
    }

    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('SUCCESSION_READ')")
    @Operation(summary = "List all successor plans for a company")
    public List<PlanResponse> listPlans(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return succession.listPlans(tenantId, companyId);
    }

    @GetMapping("/plans/{planId}/candidates")
    @PreAuthorize("hasAuthority('SUCCESSION_READ')")
    @Operation(summary = "List candidates on a successor plan")
    public List<CandidateResponse> candidatesFor(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID planId) {
        return succession.candidatesFor(tenantId, planId);
    }

    // Readiness ---------------------------------------------------------------

    @PostMapping("/readiness")
    @PreAuthorize("hasAuthority('SUCCESSION_ASSESS')")
    @Operation(summary = "Record a readiness assessment (9-box style)")
    public ResponseEntity<ReadinessResponse> recordReadiness(
            @Valid @RequestBody ReadinessRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(succession.recordReadiness(req));
    }

    @GetMapping("/readiness/{employeeId}")
    @PreAuthorize("hasAuthority('SUCCESSION_READ')")
    @Operation(summary = "List an employee's readiness assessment history")
    public List<ReadinessResponse> readinessHistory(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return succession.readinessHistory(tenantId, employeeId);
    }

    @GetMapping("/readiness/by-tier")
    @PreAuthorize("hasAuthority('SUCCESSION_READ')")
    @Operation(summary = "List readiness assessments by talent tier")
    public List<ReadinessResponse> readinessByTier(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam TalentTier tier) {
        return succession.readinessByTier(tenantId, companyId, tier);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('SUCCESSION_READ')")
    @Operation(summary = "Aggregate succession dashboard")
    public SuccessionDashboardResponse dashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return succession.dashboard(tenantId, companyId);
    }
}
