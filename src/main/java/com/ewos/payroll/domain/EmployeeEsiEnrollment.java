package com.ewos.payroll.domain;

import com.ewos.employee.domain.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Locks ESI eligibility for one employee for a full contribution period (April&ndash;September or
 * October&ndash;March), per the ESI Act: eligibility is decided once, at the later of the period's
 * start or the employee's hire date, and holds for the rest of that period regardless of later wage
 * changes. {@link com.ewos.payroll.application.EsiCalculationService} creates the row the first
 * time it evaluates an employee within a period and reuses it on every subsequent payslip in that
 * period.
 */
@Entity
@Table(name = "employee_esi_enrollments")
public class EmployeeEsiEnrollment {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "contribution_period_start", nullable = false)
    private LocalDate contributionPeriodStart;

    @Column(name = "contribution_period_end", nullable = false)
    private LocalDate contributionPeriodEnd;

    @Column(name = "eligible_at_period_start", nullable = false)
    private boolean eligibleAtPeriodStart;

    @Column(name = "wage_at_period_start", nullable = false, precision = 18, scale = 4)
    private BigDecimal wageAtPeriodStart;

    public UUID getId() {
        return id;
    }

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

    public LocalDate getContributionPeriodStart() {
        return contributionPeriodStart;
    }

    public void setContributionPeriodStart(LocalDate contributionPeriodStart) {
        this.contributionPeriodStart = contributionPeriodStart;
    }

    public LocalDate getContributionPeriodEnd() {
        return contributionPeriodEnd;
    }

    public void setContributionPeriodEnd(LocalDate contributionPeriodEnd) {
        this.contributionPeriodEnd = contributionPeriodEnd;
    }

    public boolean isEligibleAtPeriodStart() {
        return eligibleAtPeriodStart;
    }

    public void setEligibleAtPeriodStart(boolean eligibleAtPeriodStart) {
        this.eligibleAtPeriodStart = eligibleAtPeriodStart;
    }

    public BigDecimal getWageAtPeriodStart() {
        return wageAtPeriodStart;
    }

    public void setWageAtPeriodStart(BigDecimal wageAtPeriodStart) {
        this.wageAtPeriodStart = wageAtPeriodStart;
    }
}
