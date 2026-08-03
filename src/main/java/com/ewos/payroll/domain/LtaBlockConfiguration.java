package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Configures the Section 10(5) LTA (Leave Travel Allowance) exemption block a tenant/company
 * currently operates under — the government-fixed 4-calendar-year block, not the financial year.
 * {@code anchorBlockStartYear} plus {@code blockDurationYears} determine which block any given
 * calendar year falls into (e.g. anchor 2022, duration 4 → 2022-2025, 2026-2029, …), so the engine
 * never has to be redeployed just because a new block starts; only a new (or updated) row here is
 * needed. Exactly one row should be {@code active} per tenant/company at a time — history is kept
 * by leaving old rows in place with {@code effectiveTo} set rather than deleting them, mirroring
 * every other statutory-configuration entity in this codebase.
 *
 * <p><b>Sprint 24K §8.1 — statutory verification required.</b> The exact current block boundary is
 * a government-fixed fact this engine cannot safely hardcode as authoritative. The seed row's
 * {@code notes} column and {@code docs/business-rules/payroll-domain-enhancements.md} both flag
 * this explicitly — confirm the current block against the applicable CBDT/government notification
 * before relying on the seed value in production.
 */
@Entity
@Table(name = "lta_block_configurations")
@SQLDelete(
        sql =
                "UPDATE lta_block_configurations SET deleted_at = NOW() "
                        + "WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class LtaBlockConfiguration extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Null means this configuration applies to every company in the tenant. */
    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "block_duration_years", nullable = false)
    private int blockDurationYears = 4;

    @Column(name = "anchor_block_start_year", nullable = false)
    private int anchorBlockStartYear;

    @Column(name = "max_exempt_claims_per_block", nullable = false)
    private int maxExemptClaimsPerBlock = 2;

    @Column(name = "carry_forward_enabled", nullable = false)
    private boolean carryForwardEnabled = true;

    @Column(name = "carry_forward_max_claims", nullable = false)
    private int carryForwardMaxClaims = 1;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    /**
     * The block start year (inclusive) covering the given calendar year — e.g. anchor 2022,
     * duration 4, year 2027 → 2026. Purely a function of the two configured numbers, so a new block
     * never requires a code change.
     */
    public int blockStartYearFor(int calendarYear) {
        int offset = calendarYear - anchorBlockStartYear;
        int blockIndex = Math.floorDiv(offset, blockDurationYears);
        return anchorBlockStartYear + blockIndex * blockDurationYears;
    }

    public int blockEndYearFor(int calendarYear) {
        return blockStartYearFor(calendarYear) + blockDurationYears - 1;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public int getBlockDurationYears() {
        return blockDurationYears;
    }

    public void setBlockDurationYears(int blockDurationYears) {
        this.blockDurationYears = blockDurationYears;
    }

    public int getAnchorBlockStartYear() {
        return anchorBlockStartYear;
    }

    public void setAnchorBlockStartYear(int anchorBlockStartYear) {
        this.anchorBlockStartYear = anchorBlockStartYear;
    }

    public int getMaxExemptClaimsPerBlock() {
        return maxExemptClaimsPerBlock;
    }

    public void setMaxExemptClaimsPerBlock(int maxExemptClaimsPerBlock) {
        this.maxExemptClaimsPerBlock = maxExemptClaimsPerBlock;
    }

    public boolean isCarryForwardEnabled() {
        return carryForwardEnabled;
    }

    public void setCarryForwardEnabled(boolean carryForwardEnabled) {
        this.carryForwardEnabled = carryForwardEnabled;
    }

    public int getCarryForwardMaxClaims() {
        return carryForwardMaxClaims;
    }

    public void setCarryForwardMaxClaims(int carryForwardMaxClaims) {
        this.carryForwardMaxClaims = carryForwardMaxClaims;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
