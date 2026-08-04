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
 * One surcharge bracket for a {@link TaxRegime} + fiscal year, applied as a flat rate on the
 * post-rebate tax once taxable income crosses the bracket's threshold. Marginal relief is not
 * applied — see the Sprint 24H-1 design document for why.
 */
@Entity
@Table(name = "income_tax_surcharge_slabs")
@SQLDelete(
        sql =
                "UPDATE income_tax_surcharge_slabs SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class IncomeTaxSurchargeSlab extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime", nullable = false, length = 16)
    private TaxRegime regime;

    @Column(name = "fiscal_year", nullable = false, length = 16)
    private String fiscalYear;

    @Column(name = "min_income", nullable = false, precision = 18, scale = 4)
    private BigDecimal minIncome;

    @Column(name = "max_income", precision = 18, scale = 4)
    private BigDecimal maxIncome;

    @Column(name = "surcharge_rate_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal surchargeRatePct;

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

    public BigDecimal getMinIncome() {
        return minIncome;
    }

    public void setMinIncome(BigDecimal minIncome) {
        this.minIncome = minIncome;
    }

    public BigDecimal getMaxIncome() {
        return maxIncome;
    }

    public void setMaxIncome(BigDecimal maxIncome) {
        this.maxIncome = maxIncome;
    }

    public BigDecimal getSurchargeRatePct() {
        return surchargeRatePct;
    }

    public void setSurchargeRatePct(BigDecimal surchargeRatePct) {
        this.surchargeRatePct = surchargeRatePct;
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
