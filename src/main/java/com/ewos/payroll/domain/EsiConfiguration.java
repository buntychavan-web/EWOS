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
 * ESIC configuration: the monthly wage eligibility threshold and employee/employer contribution
 * rates. {@code companyId} null means the tenant-wide default.
 */
@Entity
@Table(name = "esi_configurations")
@SQLDelete(sql = "UPDATE esi_configurations SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class EsiConfiguration extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "wage_threshold", nullable = false, precision = 18, scale = 4)
    private BigDecimal wageThreshold;

    @Column(name = "employee_rate_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal employeeRatePct;

    @Column(name = "employer_rate_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal employerRatePct;

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

    public BigDecimal getWageThreshold() {
        return wageThreshold;
    }

    public void setWageThreshold(BigDecimal wageThreshold) {
        this.wageThreshold = wageThreshold;
    }

    public BigDecimal getEmployeeRatePct() {
        return employeeRatePct;
    }

    public void setEmployeeRatePct(BigDecimal employeeRatePct) {
        this.employeeRatePct = employeeRatePct;
    }

    public BigDecimal getEmployerRatePct() {
        return employerRatePct;
    }

    public void setEmployerRatePct(BigDecimal employerRatePct) {
        this.employerRatePct = employerRatePct;
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
