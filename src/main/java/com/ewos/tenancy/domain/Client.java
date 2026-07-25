package com.ewos.tenancy.domain;

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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A commercial customer relationship within a tenant — the entity being serviced. In outsourcing
 * mode, one row per outsourcing customer; a client may own many {@link Company} legal entities.
 *
 * <p>Soft-deleted with {@code deleted_at}; partial unique index on {@code (tenant_id,
 * LOWER(code))}.
 */
@Entity
@Table(name = "clients")
@SQLDelete(sql = "UPDATE clients SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class Client extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "legal_name", nullable = false, length = 256)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClientStatus status = ClientStatus.ACTIVE;

    @Column(name = "onboarded_at")
    private LocalDate onboardedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public ClientStatus getStatus() {
        return status;
    }

    public void setStatus(ClientStatus status) {
        this.status = status;
    }

    public LocalDate getOnboardedAt() {
        return onboardedAt;
    }

    public void setOnboardedAt(LocalDate onboardedAt) {
        this.onboardedAt = onboardedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
