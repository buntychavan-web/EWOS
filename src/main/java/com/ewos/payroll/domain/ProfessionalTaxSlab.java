package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * One monthly income band of a state's Professional Tax schedule. {@code gender} is nullable — null
 * applies to everyone; a narrower gender-specific row (e.g. a higher exemption threshold for women
 * in a state that grants one) is preferred by {@link
 * com.ewos.payroll.application.ProfessionalTaxCalculationService} over the gender-agnostic row when
 * both match. {@code annualCapAmount}, if set, caps the employee's cumulative PT for the fiscal
 * year — read against {@link EmployeeTaxDeclaration#getYtdProfessionalTaxPaid()}.
 */
@Entity
@Table(name = "professional_tax_slabs")
@SQLDelete(
        sql =
                "UPDATE professional_tax_slabs SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ProfessionalTaxSlab extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jurisdiction_id", nullable = false, updatable = false)
    private StatutoryJurisdiction jurisdiction;

    @Column(name = "gender", length = 16)
    private String gender;

    @Column(name = "min_monthly_income", nullable = false, precision = 18, scale = 4)
    private BigDecimal minMonthlyIncome;

    @Column(name = "max_monthly_income", precision = 18, scale = 4)
    private BigDecimal maxMonthlyIncome;

    @Column(name = "monthly_tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal monthlyTaxAmount;

    @Column(name = "annual_cap_amount", precision = 18, scale = 4)
    private BigDecimal annualCapAmount;

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

    public StatutoryJurisdiction getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(StatutoryJurisdiction jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public BigDecimal getMinMonthlyIncome() {
        return minMonthlyIncome;
    }

    public void setMinMonthlyIncome(BigDecimal minMonthlyIncome) {
        this.minMonthlyIncome = minMonthlyIncome;
    }

    public BigDecimal getMaxMonthlyIncome() {
        return maxMonthlyIncome;
    }

    public void setMaxMonthlyIncome(BigDecimal maxMonthlyIncome) {
        this.maxMonthlyIncome = maxMonthlyIncome;
    }

    public BigDecimal getMonthlyTaxAmount() {
        return monthlyTaxAmount;
    }

    public void setMonthlyTaxAmount(BigDecimal monthlyTaxAmount) {
        this.monthlyTaxAmount = monthlyTaxAmount;
    }

    public BigDecimal getAnnualCapAmount() {
        return annualCapAmount;
    }

    public void setAnnualCapAmount(BigDecimal annualCapAmount) {
        this.annualCapAmount = annualCapAmount;
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
