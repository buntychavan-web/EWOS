package com.ewos.recruitment.domain;

import com.ewos.organization.domain.OrganizationUnit;
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
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A named seat in the org chart (title + department + grade band). Long-lived: multiple
 * requisitions may reference the same position over its lifetime (backfills, expansions,
 * replacements).
 */
@Entity
@Table(name = "job_positions")
@SQLDelete(sql = "UPDATE job_positions SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class JobPosition extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_org_unit_id")
    private OrganizationUnit departmentOrgUnit;

    @Column(name = "location", length = 256)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 32)
    private EmploymentType employmentType;

    @Column(name = "grade", length = 64)
    private String grade;

    @Column(name = "salary_currency", length = 3)
    private String salaryCurrency;

    @Column(name = "salary_min", precision = 18, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 18, scale = 2)
    private BigDecimal salaryMax;

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

    public void setTenantId(UUID v) {
        this.tenantId = v;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID v) {
        this.companyId = v;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        this.code = v;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String v) {
        this.title = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public OrganizationUnit getDepartmentOrgUnit() {
        return departmentOrgUnit;
    }

    public void setDepartmentOrgUnit(OrganizationUnit v) {
        this.departmentOrgUnit = v;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String v) {
        this.location = v;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType v) {
        this.employmentType = v;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String v) {
        this.grade = v;
    }

    public String getSalaryCurrency() {
        return salaryCurrency;
    }

    public void setSalaryCurrency(String v) {
        this.salaryCurrency = v;
    }

    public BigDecimal getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(BigDecimal v) {
        this.salaryMin = v;
    }

    public BigDecimal getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(BigDecimal v) {
        this.salaryMax = v;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean v) {
        this.active = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
