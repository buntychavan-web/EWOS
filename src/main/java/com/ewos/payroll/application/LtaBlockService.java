package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.ClaimLtaJourneyRequest;
import com.ewos.payroll.api.dto.CreateLtaBlockConfigurationRequest;
import com.ewos.payroll.api.dto.CreditLtaAnnualEligibilityRequest;
import com.ewos.payroll.api.dto.LtaBlockClaimResponse;
import com.ewos.payroll.api.dto.LtaBlockConfigurationResponse;
import com.ewos.payroll.api.dto.LtaBlockSummaryResponse;
import com.ewos.payroll.domain.EmployeeLtaBlockClaim;
import com.ewos.payroll.domain.LtaBlockConfiguration;
import com.ewos.payroll.domain.LtaClaimType;
import com.ewos.payroll.infrastructure.persistence.EmployeeLtaBlockClaimRepository;
import com.ewos.payroll.infrastructure.persistence.LtaBlockConfigurationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Section 10(5) LTA block management (Sprint 24K §8.1). Maintains the LTA exemption at the
 * government-fixed 4-calendar-year <em>block</em> level rather than the financial year — the block
 * boundary itself is configuration ({@link LtaBlockConfiguration}), never a hardcoded constant, so
 * a new block starting is just a new (or updated) configuration row, not a code change. Every
 * balance this service reports (credited, claimed, tax-free, taxable, remaining claims, closing
 * balance) is derived by summing the append-only {@link EmployeeLtaBlockClaim} ledger for the block
 * in question — nothing is stored as mutable running state, so a financial-year close can never
 * erase or desynchronise block history, matching this codebase's established append-only pattern
 * ({@code TdsAdjustmentLog}, {@code WorkflowHistory}, {@code StatutoryDeduction}).
 *
 * <p><b>Statutory verification required before production rollout.</b> The exemption gate
 * implemented here is the well-established "2 journeys per 4-year block, actual travel cost, one
 * unused journey carried forward into the next block's first calendar year" rule under Section
 * 10(5) read with Rule 2B — long-standing and stable. This service does <em>not</em> hardcode any
 * mode-of-travel fare cap or a specific block's boundary years; those depend on notifications that
 * cannot be verified in this environment and are left fully configurable on {@link
 * LtaBlockConfiguration} instead (see the seed row's {@code notes} and {@code
 * docs/business-rules/payroll-domain-enhancements.md}). Confirm the tenant's actual current block
 * against the applicable government notification before relying on the seed configuration in
 * production.
 */
@Service
@Transactional
public class LtaBlockService {

    private final LtaBlockConfigurationRepository configurations;
    private final EmployeeLtaBlockClaimRepository claims;
    private final EmployeeRepository employees;
    private final PayrollMapper mapper;
    private final ClientAccessGuard guard;

    public LtaBlockService(
            LtaBlockConfigurationRepository configurations,
            EmployeeLtaBlockClaimRepository claims,
            EmployeeRepository employees,
            PayrollMapper mapper,
            ClientAccessGuard guard) {
        this.configurations = configurations;
        this.claims = claims;
        this.employees = employees;
        this.mapper = mapper;
        this.guard = guard;
    }

    public LtaBlockConfigurationResponse configure(CreateLtaBlockConfigurationRequest request) {
        guard.requireAccessForCompany(request.companyId());
        LtaBlockConfiguration c = new LtaBlockConfiguration();
        c.setTenantId(request.tenantId());
        c.setCompanyId(request.companyId());
        if (request.blockDurationYears() != null) {
            c.setBlockDurationYears(request.blockDurationYears());
        }
        c.setAnchorBlockStartYear(request.anchorBlockStartYear());
        if (request.maxExemptClaimsPerBlock() != null) {
            c.setMaxExemptClaimsPerBlock(request.maxExemptClaimsPerBlock());
        }
        if (request.carryForwardEnabled() != null) {
            c.setCarryForwardEnabled(request.carryForwardEnabled());
        }
        if (request.carryForwardMaxClaims() != null) {
            c.setCarryForwardMaxClaims(request.carryForwardMaxClaims());
        }
        c.setEffectiveFrom(request.effectiveFrom());
        c.setNotes(request.notes());
        return mapper.toResponse(configurations.save(c));
    }

    @Transactional(readOnly = true)
    public List<LtaBlockConfigurationResponse> history(UUID tenantId) {
        return configurations.findAllByTenantIdOrderByEffectiveFromDesc(tenantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Records the annual LTA eligibility credited into the employee's current block for a fiscal
     * year. Purely additive bookkeeping — does not itself consume a journey-claim slot.
     */
    public LtaBlockClaimResponse creditAnnualEligibility(CreditLtaAnnualEligibilityRequest r) {
        guard.requireAccessForCompany(r.companyId());
        Employee employee = requireEmployee(r.tenantId(), r.employeeId());
        LtaBlockConfiguration config = requireConfiguration(r.tenantId(), r.companyId());
        LocalDate today = LocalDate.now();
        int blockStartYear = config.blockStartYearFor(today.getYear());

        EmployeeLtaBlockClaim claim = new EmployeeLtaBlockClaim();
        claim.setTenantId(r.tenantId());
        claim.setCompanyId(r.companyId());
        claim.setEmployee(employee);
        claim.setBlockConfiguration(config);
        claim.setBlockStartYear(blockStartYear);
        claim.setBlockEndYear(config.blockEndYearFor(today.getYear()));
        claim.setClaimType(LtaClaimType.ANNUAL_CREDIT);
        claim.setFiscalYear(r.fiscalYear());
        claim.setClaimDate(today);
        claim.setLtaCreditedAmount(r.ltaCreditedAmount());
        claim.setNotes(r.notes());
        return mapper.toResponse(claims.save(claim));
    }

    /**
     * Records an actual journey claim against the current block. The exemption gate is the
     * well-established journey <em>count</em> (up to {@code maxExemptClaimsPerBlock}, plus any
     * carried-in slots), not an invented monetary cap: while the block still has an unused claim
     * available, the full amount is tax-free; once exhausted, the whole claim is taxable.
     */
    public LtaBlockClaimResponse claimJourney(ClaimLtaJourneyRequest r) {
        guard.requireAccessForCompany(r.companyId());
        Employee employee = requireEmployee(r.tenantId(), r.employeeId());
        LtaBlockConfiguration config = requireConfiguration(r.tenantId(), r.companyId());
        int blockStartYear = config.blockStartYearFor(r.claimDate().getYear());
        int blockEndYear = config.blockEndYearFor(r.claimDate().getYear());

        List<EmployeeLtaBlockClaim> blockLedger =
                claims.findAllForEmployeeAndBlock(r.tenantId(), r.employeeId(), blockStartYear);
        int carriedIn = carriedInClaims(blockLedger);
        int used = journeyClaimsUsed(blockLedger);
        int quota = config.getMaxExemptClaimsPerBlock() + carriedIn;
        boolean withinQuota = used < quota;

        EmployeeLtaBlockClaim claim = new EmployeeLtaBlockClaim();
        claim.setTenantId(r.tenantId());
        claim.setCompanyId(r.companyId());
        claim.setEmployee(employee);
        claim.setBlockConfiguration(config);
        claim.setBlockStartYear(blockStartYear);
        claim.setBlockEndYear(blockEndYear);
        claim.setClaimType(LtaClaimType.JOURNEY_CLAIM);
        claim.setFiscalYear(r.fiscalYear());
        claim.setClaimDate(r.claimDate());
        claim.setAmountClaimed(r.amountClaimed());
        claim.setTaxFreeAmount(withinQuota ? r.amountClaimed() : BigDecimal.ZERO);
        claim.setTaxableAmount(withinQuota ? BigDecimal.ZERO : r.amountClaimed());
        claim.setNotes(r.notes());
        return mapper.toResponse(claims.save(claim));
    }

    /**
     * Realises the carry-forward of one unused claim from the block immediately preceding {@code
     * asOf}'s block into {@code asOf}'s block — valid only while {@code asOf} still falls in that
     * next block's first calendar year, matching the statutory carry-forward window. Writes a
     * {@link LtaClaimType#BLOCK_CARRY_FORWARD} row granting the extra slot; it does not itself
     * consume a claim.
     */
    public LtaBlockClaimResponse carryForwardUnusedClaim(
            UUID tenantId, UUID companyId, UUID employeeId, LocalDate asOf, String notes) {
        guard.requireAccessForCompany(companyId);
        Employee employee = requireEmployee(tenantId, employeeId);
        LtaBlockConfiguration config = requireConfiguration(tenantId, companyId);
        if (!config.isCarryForwardEnabled()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Carry-forward is not enabled for this configuration");
        }
        int currentBlockStart = config.blockStartYearFor(asOf.getYear());
        int previousBlockStart = currentBlockStart - config.getBlockDurationYears();
        if (asOf.getYear() != currentBlockStart) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Carry-forward can only be claimed in the first calendar year of a new block");
        }

        List<EmployeeLtaBlockClaim> previousBlockLedger =
                claims.findAllForEmployeeAndBlock(tenantId, employeeId, previousBlockStart);
        int unusedInPreviousBlock =
                Math.max(
                        0,
                        (config.getMaxExemptClaimsPerBlock() + carriedInClaims(previousBlockLedger))
                                - journeyClaimsUsed(previousBlockLedger));
        int alreadyCarriedForward =
                carriedInClaims(
                        claims.findAllForEmployeeAndBlock(tenantId, employeeId, currentBlockStart));
        int available =
                Math.min(unusedInPreviousBlock, config.getCarryForwardMaxClaims())
                        - alreadyCarriedForward;
        if (available <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "No unused claim available to carry forward");
        }

        EmployeeLtaBlockClaim claim = new EmployeeLtaBlockClaim();
        claim.setTenantId(tenantId);
        claim.setCompanyId(companyId);
        claim.setEmployee(employee);
        claim.setBlockConfiguration(config);
        claim.setBlockStartYear(currentBlockStart);
        claim.setBlockEndYear(config.blockEndYearFor(asOf.getYear()));
        claim.setClaimType(LtaClaimType.BLOCK_CARRY_FORWARD);
        claim.setFiscalYear(com.ewos.payroll.domain.FiscalYear.labelFor(asOf));
        claim.setClaimDate(asOf);
        claim.setCarriedForwardFromPreviousBlock(true);
        claim.setNotes(notes);
        return mapper.toResponse(claims.save(claim));
    }

    @Transactional(readOnly = true)
    public LtaBlockSummaryResponse blockSummary(
            UUID tenantId, UUID companyId, UUID employeeId, LocalDate asOf) {
        guard.requireAccessForCompany(companyId);
        requireEmployee(tenantId, employeeId);
        LtaBlockConfiguration config = requireConfiguration(tenantId, companyId);
        int blockStartYear = config.blockStartYearFor(asOf.getYear());
        int blockEndYear = config.blockEndYearFor(asOf.getYear());

        List<EmployeeLtaBlockClaim> blockLedger =
                claims.findAllForEmployeeAndBlock(tenantId, employeeId, blockStartYear);

        int carriedIn = carriedInClaims(blockLedger);
        int used = journeyClaimsUsed(blockLedger);
        int quota = config.getMaxExemptClaimsPerBlock() + carriedIn;
        int remaining = Math.max(0, quota - used);
        int carryForwardEligible =
                config.isCarryForwardEnabled()
                        ? Math.min(remaining, config.getCarryForwardMaxClaims())
                        : 0;

        BigDecimal credited =
                sum(
                        blockLedger,
                        LtaClaimType.ANNUAL_CREDIT,
                        EmployeeLtaBlockClaim::getLtaCreditedAmount);
        BigDecimal claimedAmount =
                blockLedger.stream()
                        .filter(c -> c.getClaimType() == LtaClaimType.JOURNEY_CLAIM)
                        .map(EmployeeLtaBlockClaim::getAmountClaimed)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxFree =
                blockLedger.stream()
                        .filter(c -> c.getClaimType() == LtaClaimType.JOURNEY_CLAIM)
                        .map(EmployeeLtaBlockClaim::getTaxFreeAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxable =
                blockLedger.stream()
                        .filter(c -> c.getClaimType() == LtaClaimType.JOURNEY_CLAIM)
                        .map(EmployeeLtaBlockClaim::getTaxableAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LtaBlockClaimResponse> historyForBlock =
                blockLedger.stream().map(mapper::toResponse).toList();

        return new LtaBlockSummaryResponse(
                employeeId,
                blockStartYear,
                blockEndYear,
                config.getMaxExemptClaimsPerBlock(),
                carriedIn,
                used,
                remaining,
                carryForwardEligible,
                BigDecimal.ZERO,
                credited,
                claimedAmount,
                taxFree,
                taxable,
                credited.subtract(claimedAmount),
                historyForBlock);
    }

    /** Complete cross-block ledger for the employee — never filtered or erased by block. */
    @Transactional(readOnly = true)
    public List<LtaBlockClaimResponse> fullHistory(UUID tenantId, UUID companyId, UUID employeeId) {
        guard.requireAccessForCompany(companyId);
        requireEmployee(tenantId, employeeId);
        return claims.findAllForEmployee(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private static int carriedInClaims(List<EmployeeLtaBlockClaim> blockLedger) {
        return (int)
                blockLedger.stream()
                        .filter(c -> c.getClaimType() == LtaClaimType.BLOCK_CARRY_FORWARD)
                        .count();
    }

    private static int journeyClaimsUsed(List<EmployeeLtaBlockClaim> blockLedger) {
        return (int)
                blockLedger.stream()
                        .filter(c -> c.getClaimType() == LtaClaimType.JOURNEY_CLAIM)
                        .count();
    }

    private static BigDecimal sum(
            List<EmployeeLtaBlockClaim> ledger,
            LtaClaimType type,
            java.util.function.Function<EmployeeLtaBlockClaim, BigDecimal> extractor) {
        return ledger.stream()
                .filter(c -> c.getClaimType() == type)
                .map(extractor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Employee requireEmployee(UUID tenantId, UUID employeeId) {
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Employee not found"));
    }

    private LtaBlockConfiguration requireConfiguration(UUID tenantId, UUID companyId) {
        List<LtaBlockConfiguration> candidates =
                configurations.findActiveCandidates(tenantId, companyId);
        if (candidates.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "No active LTA block configuration for this company");
        }
        return candidates.get(0);
    }
}
