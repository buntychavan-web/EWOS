package com.ewos.onboarding.domain;

import com.ewos.employee.domain.Employee;
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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Onboarding plan for a newly-joined employee. One row per employee. */
@Entity
@Table(name = "onboarding_plans")
@SQLDelete(sql = "UPDATE onboarding_plans SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class OnboardingPlan extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "source_offer_id", updatable = false)
    private UUID sourceOfferId;

    @Column(name = "source_checklist_id", updatable = false)
    private UUID sourceChecklistId;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_employee_id")
    private Employee managerEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buddy_employee_id")
    private Employee buddyEmployee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OnboardingPlanStatus status = OnboardingPlanStatus.PLANNED;

    @Column(name = "completion_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionPercent = BigDecimal.ZERO;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "notes", length = 4000)
    private String notes;

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

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee v) {
        this.employee = v;
    }

    public UUID getSourceOfferId() {
        return sourceOfferId;
    }

    public void setSourceOfferId(UUID v) {
        this.sourceOfferId = v;
    }

    public UUID getSourceChecklistId() {
        return sourceChecklistId;
    }

    public void setSourceChecklistId(UUID v) {
        this.sourceChecklistId = v;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(LocalDate v) {
        this.joiningDate = v;
    }

    public Employee getManagerEmployee() {
        return managerEmployee;
    }

    public void setManagerEmployee(Employee v) {
        this.managerEmployee = v;
    }

    public Employee getBuddyEmployee() {
        return buddyEmployee;
    }

    public void setBuddyEmployee(Employee v) {
        this.buddyEmployee = v;
    }

    public OnboardingPlanStatus getStatus() {
        return status;
    }

    public void setStatus(OnboardingPlanStatus v) {
        this.status = v;
    }

    public BigDecimal getCompletionPercent() {
        return completionPercent;
    }

    public void setCompletionPercent(BigDecimal v) {
        this.completionPercent = v;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant v) {
        this.startedAt = v;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant v) {
        this.completedAt = v;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }

    public void setCompletedBy(UUID v) {
        this.completedBy = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
