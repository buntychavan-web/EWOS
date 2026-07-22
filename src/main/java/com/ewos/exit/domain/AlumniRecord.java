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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Alumni record for an ex-employee. */
@Entity
@Table(name = "alumni_records")
@SQLDelete(sql = "UPDATE alumni_records SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class AlumniRecord extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resignation_id")
    private Resignation resignation;

    @Column(name = "exited_on", nullable = false)
    private LocalDate exitedOn;

    @Column(name = "alumni_email", length = 256)
    private String alumniEmail;

    @Column(name = "linkedin_url", length = 512)
    private String linkedinUrl;

    @Column(name = "current_employer", length = 256)
    private String currentEmployer;

    @Column(name = "stay_in_touch", nullable = false)
    private boolean stayInTouch = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "rehire_eligibility", length = 32)
    private RehireEligibility rehireEligibility;

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

    public Resignation getResignation() {
        return resignation;
    }

    public void setResignation(Resignation v) {
        this.resignation = v;
    }

    public LocalDate getExitedOn() {
        return exitedOn;
    }

    public void setExitedOn(LocalDate v) {
        this.exitedOn = v;
    }

    public String getAlumniEmail() {
        return alumniEmail;
    }

    public void setAlumniEmail(String v) {
        this.alumniEmail = v;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String v) {
        this.linkedinUrl = v;
    }

    public String getCurrentEmployer() {
        return currentEmployer;
    }

    public void setCurrentEmployer(String v) {
        this.currentEmployer = v;
    }

    public boolean isStayInTouch() {
        return stayInTouch;
    }

    public void setStayInTouch(boolean v) {
        this.stayInTouch = v;
    }

    public RehireEligibility getRehireEligibility() {
        return rehireEligibility;
    }

    public void setRehireEligibility(RehireEligibility v) {
        this.rehireEligibility = v;
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
