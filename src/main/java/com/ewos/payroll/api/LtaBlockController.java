package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.ClaimLtaJourneyRequest;
import com.ewos.payroll.api.dto.CreateLtaBlockConfigurationRequest;
import com.ewos.payroll.api.dto.CreditLtaAnnualEligibilityRequest;
import com.ewos.payroll.api.dto.LtaBlockClaimResponse;
import com.ewos.payroll.api.dto.LtaBlockConfigurationResponse;
import com.ewos.payroll.api.dto.LtaBlockSummaryResponse;
import com.ewos.payroll.application.LtaBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/payroll/lta")
@Tag(
        name = "LTA Block Management",
        description = "Section 10(5) LTA block-level exemption tracking")
public class LtaBlockController {

    private final LtaBlockService service;

    public LtaBlockController(LtaBlockService service) {
        this.service = service;
    }

    @PostMapping("/configurations")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Configure the LTA block boundary/rules for a tenant or company")
    public LtaBlockConfigurationResponse configure(
            @Valid @RequestBody CreateLtaBlockConfigurationRequest request) {
        return service.configure(request);
    }

    @GetMapping("/configurations")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "List every LTA block configuration ever set for a tenant")
    public List<LtaBlockConfigurationResponse> configurationHistory(
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.history(tenantId);
    }

    @PostMapping("/annual-credit")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Credit an employee's annual LTA eligibility into their current block")
    public LtaBlockClaimResponse creditAnnualEligibility(
            @Valid @RequestBody CreditLtaAnnualEligibilityRequest request) {
        return service.creditAnnualEligibility(request);
    }

    @PostMapping("/claims")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Record a journey claim against the employee's current block")
    public LtaBlockClaimResponse claimJourney(@Valid @RequestBody ClaimLtaJourneyRequest request) {
        return service.claimJourney(request);
    }

    @PostMapping("/employee/{employeeId}/carry-forward")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Realise one unused claim carried forward from the previous block")
    public LtaBlockClaimResponse carryForwardUnusedClaim(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @PathVariable UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf,
            @RequestParam(required = false) String notes) {
        return service.carryForwardUnusedClaim(
                tenantId, companyId, employeeId, asOf != null ? asOf : LocalDate.now(), notes);
    }

    @GetMapping("/employee/{employeeId}/summary")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Current block summary: credited, claimed, remaining, closing balance")
    public LtaBlockSummaryResponse blockSummary(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @PathVariable UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        return service.blockSummary(
                tenantId, companyId, employeeId, asOf != null ? asOf : LocalDate.now());
    }

    @GetMapping("/employee/{employeeId}/history")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Complete cross-block LTA claim history for an employee")
    public List<LtaBlockClaimResponse> fullHistory(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @PathVariable UUID employeeId) {
        return service.fullHistory(tenantId, companyId, employeeId);
    }
}
