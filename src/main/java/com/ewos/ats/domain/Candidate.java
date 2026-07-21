package com.ewos.ats.domain;

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

/**
 * Candidate master aggregate — the identity of a hiring prospect. Same-tenant candidates share
 * uniqueness on email; phone digits are indexed (not unique) to power fuzzy duplicate detection.
 * Internal candidates carry an {@link #internalEmployee} reference so mobility flows can find the
 * current employment record without re-searching.
 */
@Entity
@Table(name = "candidates")
@SQLDelete(sql = "UPDATE candidates SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class Candidate extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "candidate_number", nullable = false, length = 64)
    private String candidateNumber;

    @Column(name = "first_name", nullable = false, length = 128)
    private String firstName;

    @Column(name = "middle_name", length = 128)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 128)
    private String lastName;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "phone_digits", length = 32)
    private String phoneDigits;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 32)
    private CandidateGender gender;

    @Column(name = "nationality", length = 64)
    private String nationality;

    @Column(name = "current_location", length = 256)
    private String currentLocation;

    @Column(name = "country", length = 64)
    private String country;

    @Column(name = "current_employer", length = 256)
    private String currentEmployer;

    @Column(name = "current_designation", length = 256)
    private String currentDesignation;

    @Column(name = "total_experience_months")
    private Integer totalExperienceMonths;

    @Column(name = "current_ctc_currency", length = 3)
    private String currentCtcCurrency;

    @Column(name = "current_ctc_amount", precision = 18, scale = 2)
    private BigDecimal currentCtcAmount;

    @Column(name = "expected_ctc_currency", length = 3)
    private String expectedCtcCurrency;

    @Column(name = "expected_ctc_amount", precision = 18, scale = 2)
    private BigDecimal expectedCtcAmount;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private CandidateSource source;

    @Column(name = "source_details", length = 512)
    private String sourceDetails;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_employee_id")
    private Employee referrerEmployee;

    @Column(name = "is_internal", nullable = false)
    private boolean internal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "internal_employee_id")
    private Employee internalEmployee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CandidateStatus status = CandidateStatus.NEW;

    @Column(name = "linkedin_url", length = 512)
    private String linkedinUrl;

    @Column(name = "github_url", length = 512)
    private String githubUrl;

    @Column(name = "portfolio_url", length = 512)
    private String portfolioUrl;

    @Column(name = "summary", length = 4000)
    private String summary;

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

    public String getCandidateNumber() {
        return candidateNumber;
    }

    public void setCandidateNumber(String v) {
        this.candidateNumber = v;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String v) {
        this.firstName = v;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String v) {
        this.middleName = v;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String v) {
        this.lastName = v;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String v) {
        this.email = v;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String v) {
        this.phone = v;
    }

    public String getPhoneDigits() {
        return phoneDigits;
    }

    public void setPhoneDigits(String v) {
        this.phoneDigits = v;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate v) {
        this.dateOfBirth = v;
    }

    public CandidateGender getGender() {
        return gender;
    }

    public void setGender(CandidateGender v) {
        this.gender = v;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String v) {
        this.nationality = v;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(String v) {
        this.currentLocation = v;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String v) {
        this.country = v;
    }

    public String getCurrentEmployer() {
        return currentEmployer;
    }

    public void setCurrentEmployer(String v) {
        this.currentEmployer = v;
    }

    public String getCurrentDesignation() {
        return currentDesignation;
    }

    public void setCurrentDesignation(String v) {
        this.currentDesignation = v;
    }

    public Integer getTotalExperienceMonths() {
        return totalExperienceMonths;
    }

    public void setTotalExperienceMonths(Integer v) {
        this.totalExperienceMonths = v;
    }

    public String getCurrentCtcCurrency() {
        return currentCtcCurrency;
    }

    public void setCurrentCtcCurrency(String v) {
        this.currentCtcCurrency = v;
    }

    public BigDecimal getCurrentCtcAmount() {
        return currentCtcAmount;
    }

    public void setCurrentCtcAmount(BigDecimal v) {
        this.currentCtcAmount = v;
    }

    public String getExpectedCtcCurrency() {
        return expectedCtcCurrency;
    }

    public void setExpectedCtcCurrency(String v) {
        this.expectedCtcCurrency = v;
    }

    public BigDecimal getExpectedCtcAmount() {
        return expectedCtcAmount;
    }

    public void setExpectedCtcAmount(BigDecimal v) {
        this.expectedCtcAmount = v;
    }

    public Integer getNoticePeriodDays() {
        return noticePeriodDays;
    }

    public void setNoticePeriodDays(Integer v) {
        this.noticePeriodDays = v;
    }

    public CandidateSource getSource() {
        return source;
    }

    public void setSource(CandidateSource v) {
        this.source = v;
    }

    public String getSourceDetails() {
        return sourceDetails;
    }

    public void setSourceDetails(String v) {
        this.sourceDetails = v;
    }

    public Employee getReferrerEmployee() {
        return referrerEmployee;
    }

    public void setReferrerEmployee(Employee v) {
        this.referrerEmployee = v;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean v) {
        this.internal = v;
    }

    public Employee getInternalEmployee() {
        return internalEmployee;
    }

    public void setInternalEmployee(Employee v) {
        this.internalEmployee = v;
    }

    public CandidateStatus getStatus() {
        return status;
    }

    public void setStatus(CandidateStatus v) {
        this.status = v;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String v) {
        this.linkedinUrl = v;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String v) {
        this.githubUrl = v;
    }

    public String getPortfolioUrl() {
        return portfolioUrl;
    }

    public void setPortfolioUrl(String v) {
        this.portfolioUrl = v;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String v) {
        this.summary = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }

    /** Full name convenience. */
    public String fullName() {
        StringBuilder sb = new StringBuilder(firstName);
        if (middleName != null && !middleName.isBlank()) {
            sb.append(' ').append(middleName);
        }
        return sb.append(' ').append(lastName).toString();
    }
}
