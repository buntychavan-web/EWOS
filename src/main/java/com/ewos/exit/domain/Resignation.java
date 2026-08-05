package com.ewos.exit.domain;

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

/** Per-employee resignation record. */
@Entity
@Table(name = "resignations")
@SQLDelete(sql = "UPDATE resignations SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class Resignation extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "resignation_type", nullable = false, length = 32, updatable = false)
    private ResignationType resignationType = ResignationType.SELF_RESIGNATION;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "intended_last_day")
    private LocalDate intendedLastDay;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "notice_period_days", nullable = false)
    private int noticePeriodDays;

    @Column(name = "notice_start_date")
    private LocalDate noticeStartDate;

    @Column(name = "notice_end_date")
    private LocalDate noticeEndDate;

    @Column(name = "buyout_days")
    private Integer buyoutDays;

    @Column(name = "buyout_amount", precision = 14, scale = 2)
    private BigDecimal buyoutAmount;

    /** Recovered from the employee for notice shortfall — the opposite direction of buyout. */
    @Column(name = "notice_recovery_amount", precision = 14, scale = 2)
    private BigDecimal noticeRecoveryAmount;

    @Column(name = "notice_waived", nullable = false)
    private boolean noticeWaived;

    @Column(name = "notice_waiver_reason", length = 2000)
    private String noticeWaiverReason;

    @Column(name = "garden_leave_start_date")
    private LocalDate gardenLeaveStartDate;

    @Column(name = "garden_leave_end_date")
    private LocalDate gardenLeaveEndDate;

    @Column(name = "notice_extension_reason", length = 2000)
    private String noticeExtensionReason;

    @Column(name = "early_release_reason", length = 2000)
    private String earlyReleaseReason;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "exit_workflow_instance_id")
    private UUID exitWorkflowInstanceId;

    /** Employee designated to take over this role during knowledge transfer (Sprint 26). */
    @Column(name = "successor_employee_id")
    private UUID successorEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ResignationStatus status = ResignationStatus.SUBMITTED;

    @Column(name = "actual_last_day")
    private LocalDate actualLastDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "rehire_eligibility", length = 32)
    private RehireEligibility rehireEligibility;

    @Column(name = "rehire_notes", length = 2000)
    private String rehireNotes;

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

    public ResignationType getResignationType() {
        return resignationType;
    }

    public void setResignationType(ResignationType v) {
        this.resignationType = v;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant v) {
        this.submittedAt = v;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(UUID v) {
        this.submittedBy = v;
    }

    public LocalDate getIntendedLastDay() {
        return intendedLastDay;
    }

    public void setIntendedLastDay(LocalDate v) {
        this.intendedLastDay = v;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String v) {
        this.reason = v;
    }

    public int getNoticePeriodDays() {
        return noticePeriodDays;
    }

    public void setNoticePeriodDays(int v) {
        this.noticePeriodDays = v;
    }

    public LocalDate getNoticeStartDate() {
        return noticeStartDate;
    }

    public void setNoticeStartDate(LocalDate v) {
        this.noticeStartDate = v;
    }

    public LocalDate getNoticeEndDate() {
        return noticeEndDate;
    }

    public void setNoticeEndDate(LocalDate v) {
        this.noticeEndDate = v;
    }

    public Integer getBuyoutDays() {
        return buyoutDays;
    }

    public void setBuyoutDays(Integer v) {
        this.buyoutDays = v;
    }

    public BigDecimal getBuyoutAmount() {
        return buyoutAmount;
    }

    public void setBuyoutAmount(BigDecimal v) {
        this.buyoutAmount = v;
    }

    public BigDecimal getNoticeRecoveryAmount() {
        return noticeRecoveryAmount;
    }

    public void setNoticeRecoveryAmount(BigDecimal v) {
        this.noticeRecoveryAmount = v;
    }

    public boolean isNoticeWaived() {
        return noticeWaived;
    }

    public void setNoticeWaived(boolean v) {
        this.noticeWaived = v;
    }

    public String getNoticeWaiverReason() {
        return noticeWaiverReason;
    }

    public void setNoticeWaiverReason(String v) {
        this.noticeWaiverReason = v;
    }

    public LocalDate getGardenLeaveStartDate() {
        return gardenLeaveStartDate;
    }

    public void setGardenLeaveStartDate(LocalDate v) {
        this.gardenLeaveStartDate = v;
    }

    public LocalDate getGardenLeaveEndDate() {
        return gardenLeaveEndDate;
    }

    public void setGardenLeaveEndDate(LocalDate v) {
        this.gardenLeaveEndDate = v;
    }

    public String getNoticeExtensionReason() {
        return noticeExtensionReason;
    }

    public void setNoticeExtensionReason(String v) {
        this.noticeExtensionReason = v;
    }

    public String getEarlyReleaseReason() {
        return earlyReleaseReason;
    }

    public void setEarlyReleaseReason(String v) {
        this.earlyReleaseReason = v;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant v) {
        this.acceptedAt = v;
    }

    public UUID getAcceptedBy() {
        return acceptedBy;
    }

    public void setAcceptedBy(UUID v) {
        this.acceptedBy = v;
    }

    public UUID getExitWorkflowInstanceId() {
        return exitWorkflowInstanceId;
    }

    public void setExitWorkflowInstanceId(UUID v) {
        this.exitWorkflowInstanceId = v;
    }

    public UUID getSuccessorEmployeeId() {
        return successorEmployeeId;
    }

    public void setSuccessorEmployeeId(UUID v) {
        this.successorEmployeeId = v;
    }

    public ResignationStatus getStatus() {
        return status;
    }

    public void setStatus(ResignationStatus v) {
        this.status = v;
    }

    public LocalDate getActualLastDay() {
        return actualLastDay;
    }

    public void setActualLastDay(LocalDate v) {
        this.actualLastDay = v;
    }

    public RehireEligibility getRehireEligibility() {
        return rehireEligibility;
    }

    public void setRehireEligibility(RehireEligibility v) {
        this.rehireEligibility = v;
    }

    public String getRehireNotes() {
        return rehireNotes;
    }

    public void setRehireNotes(String v) {
        this.rehireNotes = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
