package com.ewos.payroll.domain;

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
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Routing rule: given a {@link GLMappingSourceKind} + source_code (e.g. component code, statutory
 * code, or {@code NET} / {@code PROVISION}-slug), produce a pair of journal lines against {@code
 * debitAccount} and {@code creditAccount}. The optional {@code allocationDimension} controls
 * whether the debit is split across cost centres / business units / departments.
 */
@Entity
@Table(name = "gl_mappings")
@SQLDelete(sql = "UPDATE gl_mappings SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class GLMapping extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 32)
    private GLMappingSourceKind sourceKind;

    @Column(name = "source_code", nullable = false, length = 64)
    private String sourceCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debit_account_id", nullable = false)
    private GLAccount debitAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_account_id", nullable = false)
    private GLAccount creditAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_dimension", nullable = false, length = 32)
    private AllocationDimension allocationDimension = AllocationDimension.NONE;

    @Column(name = "description", length = 512)
    private String description;

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

    public void setTenantId(UUID v) {
        this.tenantId = v;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID v) {
        this.companyId = v;
    }

    public GLMappingSourceKind getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(GLMappingSourceKind v) {
        this.sourceKind = v;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String v) {
        this.sourceCode = v;
    }

    public GLAccount getDebitAccount() {
        return debitAccount;
    }

    public void setDebitAccount(GLAccount v) {
        this.debitAccount = v;
    }

    public GLAccount getCreditAccount() {
        return creditAccount;
    }

    public void setCreditAccount(GLAccount v) {
        this.creditAccount = v;
    }

    public AllocationDimension getAllocationDimension() {
        return allocationDimension;
    }

    public void setAllocationDimension(AllocationDimension v) {
        this.allocationDimension = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean v) {
        this.active = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
