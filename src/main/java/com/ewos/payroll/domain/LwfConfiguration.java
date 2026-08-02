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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Labour Welfare Fund scheme for one state: flat employee/employer contribution amounts and which
 * calendar months they're remitted in. {@code remittanceMonths} is a comma-separated list of month
 * numbers (e.g. {@code "6,12"} for a half-yearly scheme); {@code null} means every month.
 */
@Entity
@Table(name = "lwf_configurations")
@SQLDelete(sql = "UPDATE lwf_configurations SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class LwfConfiguration extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jurisdiction_id", nullable = false, updatable = false)
    private StatutoryJurisdiction jurisdiction;

    @Column(name = "employee_contribution", nullable = false, precision = 18, scale = 4)
    private BigDecimal employeeContribution;

    @Column(name = "employer_contribution", nullable = false, precision = 18, scale = 4)
    private BigDecimal employerContribution;

    @Column(name = "remittance_months", length = 32)
    private String remittanceMonths;

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

    public BigDecimal getEmployeeContribution() {
        return employeeContribution;
    }

    public void setEmployeeContribution(BigDecimal employeeContribution) {
        this.employeeContribution = employeeContribution;
    }

    public BigDecimal getEmployerContribution() {
        return employerContribution;
    }

    public void setEmployerContribution(BigDecimal employerContribution) {
        this.employerContribution = employerContribution;
    }

    public String getRemittanceMonths() {
        return remittanceMonths;
    }

    public void setRemittanceMonths(String remittanceMonths) {
        this.remittanceMonths = remittanceMonths;
    }

    /** True if {@code month} (1-12) is a remittance month — every month when unconfigured. */
    public boolean appliesInMonth(int month) {
        if (remittanceMonths == null || remittanceMonths.isBlank()) {
            return true;
        }
        List<String> months = Arrays.asList(remittanceMonths.split(","));
        return months.contains(Integer.toString(month));
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
