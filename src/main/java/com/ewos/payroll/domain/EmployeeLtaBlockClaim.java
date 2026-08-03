package com.ewos.payroll.domain;

import com.ewos.employee.domain.Employee;
import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Append-only ledger row recording one LTA block event for an employee — an annual eligibility
 * credit, an actual journey claim, or a carried-forward journey from the previous block (Sprint 24K
 * §8.1). Rows are never updated or deleted: block-wise opening/closing balance, claimed count, and
 * remaining tax-free claims are all derived by summing this table for the block in question,
 * exactly like every other append-only history table in this codebase ({@code StatutoryDeduction},
 * {@code WorkflowHistory}, {@code TdsAdjustmentLog}). A financial-year close never touches this
 * table, so block history is never erased by one — only a new block boundary (via {@link
 * LtaBlockConfiguration}) changes which block new rows fall into.
 */
@Entity
@Table(name = "employee_lta_block_claims")
public class EmployeeLtaBlockClaim extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lta_block_configuration_id", nullable = false, updatable = false)
    private LtaBlockConfiguration blockConfiguration;

    @Column(name = "block_start_year", nullable = false, updatable = false)
    private int blockStartYear;

    @Column(name = "block_end_year", nullable = false, updatable = false)
    private int blockEndYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 24, updatable = false)
    private LtaClaimType claimType;

    @Column(name = "fiscal_year", nullable = false, length = 16, updatable = false)
    private String fiscalYear;

    @Column(name = "claim_date", nullable = false, updatable = false)
    private LocalDate claimDate;

    @Column(
            name = "lta_credited_amount",
            nullable = false,
            precision = 18,
            scale = 4,
            updatable = false)
    private BigDecimal ltaCreditedAmount = BigDecimal.ZERO;

    @Column(name = "amount_claimed", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal amountClaimed = BigDecimal.ZERO;

    @Column(
            name = "tax_free_amount",
            nullable = false,
            precision = 18,
            scale = 4,
            updatable = false)
    private BigDecimal taxFreeAmount = BigDecimal.ZERO;

    @Column(name = "taxable_amount", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "carried_forward_from_previous_block", nullable = false, updatable = false)
    private boolean carriedForwardFromPreviousBlock;

    @Column(name = "notes", length = 2000, updatable = false)
    private String notes;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

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

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LtaBlockConfiguration getBlockConfiguration() {
        return blockConfiguration;
    }

    public void setBlockConfiguration(LtaBlockConfiguration blockConfiguration) {
        this.blockConfiguration = blockConfiguration;
    }

    public int getBlockStartYear() {
        return blockStartYear;
    }

    public void setBlockStartYear(int blockStartYear) {
        this.blockStartYear = blockStartYear;
    }

    public int getBlockEndYear() {
        return blockEndYear;
    }

    public void setBlockEndYear(int blockEndYear) {
        this.blockEndYear = blockEndYear;
    }

    public LtaClaimType getClaimType() {
        return claimType;
    }

    public void setClaimType(LtaClaimType claimType) {
        this.claimType = claimType;
    }

    public String getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(String fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public LocalDate getClaimDate() {
        return claimDate;
    }

    public void setClaimDate(LocalDate claimDate) {
        this.claimDate = claimDate;
    }

    public BigDecimal getLtaCreditedAmount() {
        return ltaCreditedAmount;
    }

    public void setLtaCreditedAmount(BigDecimal ltaCreditedAmount) {
        this.ltaCreditedAmount = ltaCreditedAmount;
    }

    public BigDecimal getAmountClaimed() {
        return amountClaimed;
    }

    public void setAmountClaimed(BigDecimal amountClaimed) {
        this.amountClaimed = amountClaimed;
    }

    public BigDecimal getTaxFreeAmount() {
        return taxFreeAmount;
    }

    public void setTaxFreeAmount(BigDecimal taxFreeAmount) {
        this.taxFreeAmount = taxFreeAmount;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public void setTaxableAmount(BigDecimal taxableAmount) {
        this.taxableAmount = taxableAmount;
    }

    public boolean isCarriedForwardFromPreviousBlock() {
        return carriedForwardFromPreviousBlock;
    }

    public void setCarriedForwardFromPreviousBlock(boolean carriedForwardFromPreviousBlock) {
        this.carriedForwardFromPreviousBlock = carriedForwardFromPreviousBlock;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
