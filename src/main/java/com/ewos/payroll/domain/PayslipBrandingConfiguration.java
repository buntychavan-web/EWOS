package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Per-company employer branding for generated payslip PDFs (Sprint 24K item 3): display name,
 * address, support contact, and footer note. {@code logoStorageUri} is metadata only — mirroring
 * this codebase's document-metadata+storageUri convention — the PDF generator never fetches or
 * embeds the image itself; the actual rendered logo is a documented follow-up once a page can
 * safely resolve that URI at generation time. {@code passwordPolicy} controls whether/how the
 * generated PDF is password-protected.
 */
@Entity
@Table(name = "payslip_branding_configurations")
@SQLDelete(
        sql =
                "UPDATE payslip_branding_configurations SET deleted_at = NOW() "
                        + "WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class PayslipBrandingConfiguration extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "support_email", length = 255)
    private String supportEmail;

    @Column(name = "footer_note", length = 1000)
    private String footerNote;

    @Column(name = "logo_storage_uri", length = 2000)
    private String logoStorageUri;

    @Enumerated(EnumType.STRING)
    @Column(name = "password_policy", nullable = false, length = 32)
    private PayslipPasswordPolicy passwordPolicy = PayslipPasswordPolicy.NONE;

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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }

    public String getFooterNote() {
        return footerNote;
    }

    public void setFooterNote(String footerNote) {
        this.footerNote = footerNote;
    }

    public String getLogoStorageUri() {
        return logoStorageUri;
    }

    public void setLogoStorageUri(String logoStorageUri) {
        this.logoStorageUri = logoStorageUri;
    }

    public PayslipPasswordPolicy getPasswordPolicy() {
        return passwordPolicy;
    }

    public void setPasswordPolicy(PayslipPasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
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
