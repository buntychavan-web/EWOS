package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Derived, point-in-time view of an employee's current LTA block — every figure is computed by
 * summing {@link LtaBlockClaimResponse} history for the block, never stored as mutable state, so a
 * financial-year close can never desynchronise it from the underlying ledger.
 *
 * <p>{@code openingBalance} is always zero: Section 10(5) carries forward an unused <em>journey
 * slot</em> into the next block's first calendar year, not unspent money, so a block never opens
 * with a monetary balance. {@code closingBalance} is the running (credited − claimed) amount within
 * the current block to date.
 */
public record LtaBlockSummaryResponse(
        UUID employeeId,
        int blockStartYear,
        int blockEndYear,
        int maxExemptClaimsPerBlock,
        int carriedInClaims,
        int journeyClaimsUsed,
        int remainingTaxFreeClaims,
        int carryForwardEligibleIntoNextBlock,
        BigDecimal openingBalance,
        BigDecimal annualLtaEligibilityCredited,
        BigDecimal ltaClaimedAmount,
        BigDecimal taxFreeClaimedAmount,
        BigDecimal taxableClaimedAmount,
        BigDecimal closingBalance,
        List<LtaBlockClaimResponse> blockHistory) {}
