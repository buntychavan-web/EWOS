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
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Which provider-staff user is authorized to work on which client, optionally narrowed to one
 * service. <b>This is the Chinese Wall enforcement table</b> — see {@link
 * com.ewos.tenancy.application.ClientAccessGuard}.
 *
 * <p>{@code userId} is a soft reference into {@code identity.users} (UUID, no FK) — the same
 * cross-module convention already used platform-wide for {@code created_by}/{@code updated_by}, and
 * documented explicitly for {@code employees.person_id} ("soft reference — no FK yet"). A null
 * {@code service} means full access to everything enabled for the client; a specific value narrows
 * the grant to that one service.
 *
 * <p>Effective-dated ({@code effectiveFrom}/{@code effectiveTo}), matching the platform's existing
 * convention for time-bounded assignments (e.g. {@code organization_node_inheritance_overrides}).
 */
@Entity
@Table(name = "client_assignments")
@SQLDelete(sql = "UPDATE client_assignments SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ClientAssignment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false, updatable = false)
    private PayrollServiceProvider provider;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ServiceOffering service;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_role", nullable = false, length = 32)
    private ClientAssignmentScopeRole scopeRole;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public PayrollServiceProvider getProvider() {
        return provider;
    }

    public void setProvider(PayrollServiceProvider provider) {
        this.provider = provider;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public ServiceOffering getService() {
        return service;
    }

    public void setService(ServiceOffering service) {
        this.service = service;
    }

    public ClientAssignmentScopeRole getScopeRole() {
        return scopeRole;
    }

    public void setScopeRole(ClientAssignmentScopeRole scopeRole) {
        this.scopeRole = scopeRole;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
