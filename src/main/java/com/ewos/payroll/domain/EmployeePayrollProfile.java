package com.ewos.payroll.domain;

import com.ewos.employee.domain.Employee;
import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Payroll assignment for a single employee: which pay group they belong to, which tax regime they
 * opted into, and the country-specific statutory identifiers (PAN, PF UAN, SSN, national tax ID)
 * stored as a JSON map so we do not schema-drift for every new country. At most one active profile
 * per employee.
 */
@Entity
@Table(name = "employee_payroll_profiles")
@SQLDelete(
        sql =
                "UPDATE employee_payroll_profiles SET deleted_at = NOW() "
                        + "WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class EmployeePayrollProfile extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pay_group_id")
    private PayGroup payGroup;

    @Column(name = "tax_regime", length = 64)
    private String taxRegime;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "statutory_identifiers", nullable = false, columnDefinition = "jsonb")
    private String statutoryIdentifiersJson = "{}";

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

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public PayGroup getPayGroup() {
        return payGroup;
    }

    public void setPayGroup(PayGroup payGroup) {
        this.payGroup = payGroup;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getStatutoryIdentifiersJson() {
        return statutoryIdentifiersJson;
    }

    public void setStatutoryIdentifiersJson(String statutoryIdentifiersJson) {
        this.statutoryIdentifiersJson =
                statutoryIdentifiersJson == null || statutoryIdentifiersJson.isBlank()
                        ? "{}"
                        : statutoryIdentifiersJson;
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
