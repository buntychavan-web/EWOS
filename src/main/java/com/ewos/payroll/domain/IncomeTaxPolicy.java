package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Non-slab income-tax parameters for one {@link TaxRegime} + fiscal year: Section 87A rebate,
 * health &amp; education cess, standard deduction, the Chapter VI-A deduction cap (old regime
 * only), and the Section 24(b) house-property-loss set-off cap.
 */
@Entity
@Table(name = "income_tax_policies")
@SQLDelete(
        sql = "UPDATE income_tax_policies SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class IncomeTaxPolicy extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime", nullable = false, length = 16)
    private TaxRegime regime;

    @Column(name = "fiscal_year", nullable = false, length = 16)
    private String fiscalYear;

    @Column(name = "rebate_income_threshold", nullable = false, precision = 18, scale = 4)
    private BigDecimal rebateIncomeThreshold = BigDecimal.ZERO;

    @Column(name = "rebate_max_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal rebateMaxAmount = BigDecimal.ZERO;

    @Column(name = "cess_rate_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal cessRatePct = BigDecimal.ZERO;

    @Column(name = "standard_deduction", nullable = false, precision = 18, scale = 4)
    private BigDecimal standardDeduction = BigDecimal.ZERO;

    @Column(name = "chapter_via_max_deduction", nullable = false, precision = 18, scale = 4)
    private BigDecimal chapterViaMaxDeduction = BigDecimal.ZERO;

    @Column(name = "house_property_loss_cap", nullable = false, precision = 18, scale = 4)
    private BigDecimal housePropertyLossCap = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public TaxRegime getRegime() {
        return regime;
    }

    public void setRegime(TaxRegime regime) {
        this.regime = regime;
    }

    public String getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(String fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public BigDecimal getRebateIncomeThreshold() {
        return rebateIncomeThreshold;
    }

    public void setRebateIncomeThreshold(BigDecimal rebateIncomeThreshold) {
        this.rebateIncomeThreshold = rebateIncomeThreshold;
    }

    public BigDecimal getRebateMaxAmount() {
        return rebateMaxAmount;
    }

    public void setRebateMaxAmount(BigDecimal rebateMaxAmount) {
        this.rebateMaxAmount = rebateMaxAmount;
    }

    public BigDecimal getCessRatePct() {
        return cessRatePct;
    }

    public void setCessRatePct(BigDecimal cessRatePct) {
        this.cessRatePct = cessRatePct;
    }

    public BigDecimal getStandardDeduction() {
        return standardDeduction;
    }

    public void setStandardDeduction(BigDecimal standardDeduction) {
        this.standardDeduction = standardDeduction;
    }

    public BigDecimal getChapterViaMaxDeduction() {
        return chapterViaMaxDeduction;
    }

    public void setChapterViaMaxDeduction(BigDecimal chapterViaMaxDeduction) {
        this.chapterViaMaxDeduction = chapterViaMaxDeduction;
    }

    public BigDecimal getHousePropertyLossCap() {
        return housePropertyLossCap;
    }

    public void setHousePropertyLossCap(BigDecimal housePropertyLossCap) {
        this.housePropertyLossCap = housePropertyLossCap;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
