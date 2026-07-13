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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Append-only structural-change record for an organization node. One row per change (create /
 * rename / move / merge_into / split_from / deactivate). Historical org structure at any past date
 * is reconstructed by reading the rows whose effective window covers that date.
 */
@Entity
@Table(name = "organization_node_versions")
public class OrganizationNodeVersion extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    private OrganizationNode node;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    private NodeChangeType changeType;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "snapshot_code", nullable = false, length = 50)
    private String snapshotCode;

    @Column(name = "snapshot_name", nullable = false, length = 255)
    private String snapshotName;

    @Column(name = "snapshot_parent_id")
    private UUID snapshotParentId;

    @Column(name = "snapshot_level_id", nullable = false)
    private UUID snapshotLevelId;

    @Column(name = "related_node_id")
    private UUID relatedNodeId;

    @Column(name = "notes", length = 500)
    private String notes;

    public OrganizationNode getNode() {
        return node;
    }

    public void setNode(OrganizationNode node) {
        this.node = node;
    }

    public NodeChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(NodeChangeType changeType) {
        this.changeType = changeType;
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

    public String getSnapshotCode() {
        return snapshotCode;
    }

    public void setSnapshotCode(String snapshotCode) {
        this.snapshotCode = snapshotCode;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public void setSnapshotName(String snapshotName) {
        this.snapshotName = snapshotName;
    }

    public UUID getSnapshotParentId() {
        return snapshotParentId;
    }

    public void setSnapshotParentId(UUID snapshotParentId) {
        this.snapshotParentId = snapshotParentId;
    }

    public UUID getSnapshotLevelId() {
        return snapshotLevelId;
    }

    public void setSnapshotLevelId(UUID snapshotLevelId) {
        this.snapshotLevelId = snapshotLevelId;
    }

    public UUID getRelatedNodeId() {
        return relatedNodeId;
    }

    public void setRelatedNodeId(UUID relatedNodeId) {
        this.relatedNodeId = relatedNodeId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
