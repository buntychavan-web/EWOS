package com.ewos.payroll.domain;

/** What kind of event an {@link EmployeeLtaBlockClaim} ledger row records. */
public enum LtaClaimType {
    /** The employee's annual LTA eligibility being credited into the current block. */
    ANNUAL_CREDIT,
    /** An actual travel claim submitted against the block's available exemption. */
    JOURNEY_CLAIM,
    /** One unused journey from the previous block carried forward into this block. */
    BLOCK_CARRY_FORWARD
}
