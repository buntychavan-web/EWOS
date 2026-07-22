package com.ewos.learning.domain;

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

/** Certification issued to an employee. */
@Entity
@Table(name = "certifications")
@SQLDelete(sql = "UPDATE certifications SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class Certification extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private TrainingCourse course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id")
    private TrainingEnrollment enrollment;

    @Column(name = "certification_name", nullable = false, length = 256)
    private String certificationName;

    @Column(name = "issuing_body", length = 256)
    private String issuingBody;

    @Column(name = "reference_number", length = 128)
    private String referenceNumber;

    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CertificationStatus status = CertificationStatus.ACTIVE;

    @Column(name = "certificate_uri", length = 1024)
    private String certificateUri;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 2000)
    private String revocationReason;

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

    public TrainingCourse getCourse() {
        return course;
    }

    public void setCourse(TrainingCourse v) {
        this.course = v;
    }

    public TrainingEnrollment getEnrollment() {
        return enrollment;
    }

    public void setEnrollment(TrainingEnrollment v) {
        this.enrollment = v;
    }

    public String getCertificationName() {
        return certificationName;
    }

    public void setCertificationName(String v) {
        this.certificationName = v;
    }

    public String getIssuingBody() {
        return issuingBody;
    }

    public void setIssuingBody(String v) {
        this.issuingBody = v;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String v) {
        this.referenceNumber = v;
    }

    public LocalDate getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDate v) {
        this.issuedAt = v;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDate v) {
        this.expiresAt = v;
    }

    public CertificationStatus getStatus() {
        return status;
    }

    public void setStatus(CertificationStatus v) {
        this.status = v;
    }

    public String getCertificateUri() {
        return certificateUri;
    }

    public void setCertificateUri(String v) {
        this.certificateUri = v;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant v) {
        this.revokedAt = v;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String v) {
        this.revocationReason = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
