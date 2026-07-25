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
 * The engagement/contract between one {@link Client} and the {@link PayrollServiceProvider}
 * servicing it: what's in scope, and the coarse contractual SLA. Task-level SLAs remain the
 * Workflow engine's existing {@code sla_hours}/{@code due_at} fields (Sprint 14.3, not built here)
 * — this table only carries the contract-level figure, per the approved design.
 *
 * <p>Soft-deleted with {@code deleted_at}; partial unique index on {@code (client_id, provider_id)}
 * restricted to {@code status = 'ACTIVE'} rows — one active engagement per pair at a time.
 */
@Entity
@Table(name = "payroll_collaborations")
@SQLDelete(
        sql =
                "UPDATE payroll_collaborations SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class PayrollCollaboration extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false, updatable = false)
    private PayrollServiceProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    private PayrollCollaborationScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PayrollCollaborationStatus status = PayrollCollaborationStatus.ACTIVE;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "sla_days")
    private Integer slaDays;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public PayrollServiceProvider getProvider() {
        return provider;
    }

    public void setProvider(PayrollServiceProvider provider) {
        this.provider = provider;
    }

    public PayrollCollaborationScope getScope() {
        return scope;
    }

    public void setScope(PayrollCollaborationScope scope) {
        this.scope = scope;
    }

    public PayrollCollaborationStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollCollaborationStatus status) {
        this.status = status;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Integer getSlaDays() {
        return slaDays;
    }

    public void setSlaDays(Integer slaDays) {
        this.slaDays = slaDays;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
