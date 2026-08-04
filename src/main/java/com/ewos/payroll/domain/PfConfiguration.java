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
 * Provident Fund configuration: wage ceiling, EPS wage ceiling, and the employee / employer-PF /
 * EPS rates {@link com.ewos.payroll.application.PfCalculationService} applies. {@code companyId}
 * null means the tenant-wide default; a specific company row overrides it.
 */
@Entity
@Table(name = "pf_configurations")
@SQLDelete(sql = "UPDATE pf_configurations SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class PfConfiguration extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "wage_ceiling", nullable = false, precision = 18, scale = 4)
    private BigDecimal wageCeiling;

    @Column(name = "eps_wage_ceiling", nullable = false, precision = 18, scale = 4)
    private BigDecimal epsWageCeiling;

    @Column(name = "employee_rate_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal employeeRatePct;

    @Column(name = "employer_pf_rate_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal employerPfRatePct;

    @Column(name = "eps_rate_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal epsRatePct;

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

    public BigDecimal getWageCeiling() {
        return wageCeiling;
    }

    public void setWageCeiling(BigDecimal wageCeiling) {
        this.wageCeiling = wageCeiling;
    }

    public BigDecimal getEpsWageCeiling() {
        return epsWageCeiling;
    }

    public void setEpsWageCeiling(BigDecimal epsWageCeiling) {
        this.epsWageCeiling = epsWageCeiling;
    }

    public BigDecimal getEmployeeRatePct() {
        return employeeRatePct;
    }

    public void setEmployeeRatePct(BigDecimal employeeRatePct) {
        this.employeeRatePct = employeeRatePct;
    }

    public BigDecimal getEmployerPfRatePct() {
        return employerPfRatePct;
    }

    public void setEmployerPfRatePct(BigDecimal employerPfRatePct) {
        this.employerPfRatePct = employerPfRatePct;
    }

    public BigDecimal getEpsRatePct() {
        return epsRatePct;
    }

    public void setEpsRatePct(BigDecimal epsRatePct) {
        this.epsRatePct = epsRatePct;
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
