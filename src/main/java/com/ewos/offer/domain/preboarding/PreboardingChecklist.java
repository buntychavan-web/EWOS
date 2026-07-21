package com.ewos.offer.domain.preboarding;

import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.JobApplication;
import com.ewos.employee.domain.Employee;
import com.ewos.offer.domain.Offer;
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

/** A pre-boarding checklist attached to an accepted offer. */
@Entity
@Table(name = "preboarding_checklists")
@SQLDelete(
        sql =
                "UPDATE preboarding_checklists SET deleted_at = NOW()"
                        + " WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class PreboardingChecklist extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false, updatable = false)
    private Offer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private JobApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PreboardingChecklistStatus status = PreboardingChecklistStatus.PENDING;

    @Column(name = "completion_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionPercent = BigDecimal.ZERO;

    @Column(name = "joining_confirmed_at")
    private Instant joiningConfirmedAt;

    @Column(name = "joining_confirmed_by")
    private UUID joiningConfirmedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

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

    public Offer getOffer() {
        return offer;
    }

    public void setOffer(Offer v) {
        this.offer = v;
    }

    public JobApplication getApplication() {
        return application;
    }

    public void setApplication(JobApplication v) {
        this.application = v;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate v) {
        this.candidate = v;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(LocalDate v) {
        this.joiningDate = v;
    }

    public PreboardingChecklistStatus getStatus() {
        return status;
    }

    public void setStatus(PreboardingChecklistStatus v) {
        this.status = v;
    }

    public BigDecimal getCompletionPercent() {
        return completionPercent;
    }

    public void setCompletionPercent(BigDecimal v) {
        this.completionPercent = v;
    }

    public Instant getJoiningConfirmedAt() {
        return joiningConfirmedAt;
    }

    public void setJoiningConfirmedAt(Instant v) {
        this.joiningConfirmedAt = v;
    }

    public UUID getJoiningConfirmedBy() {
        return joiningConfirmedBy;
    }

    public void setJoiningConfirmedBy(UUID v) {
        this.joiningConfirmedBy = v;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee v) {
        this.employee = v;
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
