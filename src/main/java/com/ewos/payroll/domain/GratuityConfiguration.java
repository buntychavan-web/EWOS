package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Gratuity formula parameters: the rate ({@code rateNumerator}/{@code rateDenominator}, 15/26 under
 * current Indian law), the statutory ceiling on the calculated amount, and the minimum years of
 * continuous service required (waivable only for death/disablement — see {@link
 * com.ewos.payroll.application.GratuityCalculationService}).
 */
@Entity
@Table(name = "gratuity_configurations")
@SQLDelete(
        sql =
                "UPDATE gratuity_configurations SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class GratuityConfiguration extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "statutory_ceiling", nullable = false, precision = 18, scale = 4)
    private BigDecimal statutoryCeiling;

    @Column(name = "rate_numerator", nullable = false)
    private int rateNumerator = 15;

    @Column(name = "rate_denominator", nullable = false)
    private int rateDenominator = 26;

    @Column(name = "min_years_eligibility", nullable = false, precision = 4, scale = 2)
    private BigDecimal minYearsEligibility = new BigDecimal("5");

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

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

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public BigDecimal getStatutoryCeiling() {
        return statutoryCeiling;
    }

    public void setStatutoryCeiling(BigDecimal statutoryCeiling) {
        this.statutoryCeiling = statutoryCeiling;
    }

    public int getRateNumerator() {
        return rateNumerator;
    }

    public void setRateNumerator(int rateNumerator) {
        this.rateNumerator = rateNumerator;
    }

    public int getRateDenominator() {
        return rateDenominator;
    }

    public void setRateDenominator(int rateDenominator) {
        this.rateDenominator = rateDenominator;
    }

    public BigDecimal getMinYearsEligibility() {
        return minYearsEligibility;
    }

    public void setMinYearsEligibility(BigDecimal minYearsEligibility) {
        this.minYearsEligibility = minYearsEligibility;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
