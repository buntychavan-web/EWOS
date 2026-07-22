package com.ewos.goals.domain;

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

/** Assigned goal — one row per assignment. */
@Entity
@Table(name = "goals")
@SQLDelete(sql = "UPDATE goals SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class Goal extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_goal_id")
    private GoalLibraryItem libraryGoal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_goal_id")
    private Goal parentGoal;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", nullable = false, length = 16)
    private GoalType goalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private GoalScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "performance_cycle_id")
    private UUID performanceCycleId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "weightage", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightage = BigDecimal.ZERO;

    @Column(name = "target", length = 256)
    private String target;

    @Column(name = "unit_of_measure", length = 64)
    private String unitOfMeasure;

    @Column(name = "current_value", length = 256)
    private String currentValue;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private GoalStatus status = GoalStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private GoalPriority priority = GoalPriority.MEDIUM;

    @Column(name = "review_score", precision = 5, scale = 2)
    private BigDecimal reviewScore;

    @Column(name = "review_notes", length = 4000)
    private String reviewNotes;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

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

    public GoalLibraryItem getLibraryGoal() {
        return libraryGoal;
    }

    public void setLibraryGoal(GoalLibraryItem v) {
        this.libraryGoal = v;
    }

    public Goal getParentGoal() {
        return parentGoal;
    }

    public void setParentGoal(Goal v) {
        this.parentGoal = v;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        this.code = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public GoalType getGoalType() {
        return goalType;
    }

    public void setGoalType(GoalType v) {
        this.goalType = v;
    }

    public GoalScope getScope() {
        return scope;
    }

    public void setScope(GoalScope v) {
        this.scope = v;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee v) {
        this.employee = v;
    }

    public UUID getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(UUID v) {
        this.orgUnitId = v;
    }

    public UUID getPerformanceCycleId() {
        return performanceCycleId;
    }

    public void setPerformanceCycleId(UUID v) {
        this.performanceCycleId = v;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate v) {
        this.periodStart = v;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate v) {
        this.periodEnd = v;
    }

    public BigDecimal getWeightage() {
        return weightage;
    }

    public void setWeightage(BigDecimal v) {
        this.weightage = v;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String v) {
        this.target = v;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String v) {
        this.unitOfMeasure = v;
    }

    public String getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(String v) {
        this.currentValue = v;
    }

    public BigDecimal getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(BigDecimal v) {
        this.progressPercent = v;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public void setStatus(GoalStatus v) {
        this.status = v;
    }

    public GoalPriority getPriority() {
        return priority;
    }

    public void setPriority(GoalPriority v) {
        this.priority = v;
    }

    public BigDecimal getReviewScore() {
        return reviewScore;
    }

    public void setReviewScore(BigDecimal v) {
        this.reviewScore = v;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String v) {
        this.reviewNotes = v;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant v) {
        this.reviewedAt = v;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(UUID v) {
        this.reviewedBy = v;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant v) {
        this.closedAt = v;
    }

    public UUID getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(UUID v) {
        this.closedBy = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
