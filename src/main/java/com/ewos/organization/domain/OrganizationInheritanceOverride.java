package com.ewos.organization.domain;

import com.ewos.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Effective-dated override for an inheritable resource on a single node. When a node has no live
 * override for a given kind, the inheritance service walks up the parent chain until it finds one —
 * or falls back to the company-level assignment.
 */
@Entity
@Table(name = "organization_node_inheritance_overrides")
public class OrganizationInheritanceOverride extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    private OrganizationNode node;

    @Enumerated(EnumType.STRING)
    @Column(name = "inheritable_kind", nullable = false, length = 40)
    private InheritableKind inheritableKind;

    @Column(name = "override_ref", nullable = false)
    private UUID overrideRef;

    @Column(name = "override_label", length = 255)
    private String overrideLabel;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public OrganizationNode getNode() {
        return node;
    }

    public void setNode(OrganizationNode node) {
        this.node = node;
    }

    public InheritableKind getInheritableKind() {
        return inheritableKind;
    }

    public void setInheritableKind(InheritableKind inheritableKind) {
        this.inheritableKind = inheritableKind;
    }

    public UUID getOverrideRef() {
        return overrideRef;
    }

    public void setOverrideRef(UUID overrideRef) {
        this.overrideRef = overrideRef;
    }

    public String getOverrideLabel() {
        return overrideLabel;
    }

    public void setOverrideLabel(String overrideLabel) {
        this.overrideLabel = overrideLabel;
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

    public long getVersion() {
        return version;
    }
}
