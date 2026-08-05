package com.ewos.exit.domain;

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

/** A single checklist line within an {@link ExitChecklistTemplate} — one clearance-worthy asset. */
@Entity
@Table(name = "exit_checklist_template_items")
@SQLDelete(
        sql =
                "UPDATE exit_checklist_template_items SET deleted_at = NOW() WHERE id = ? AND"
                        + " version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ExitChecklistItemTemplate extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private ExitChecklistTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false, length = 32)
    private ClearanceDepartment department;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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

    public ExitChecklistTemplate getTemplate() {
        return template;
    }

    public void setTemplate(ExitChecklistTemplate v) {
        this.template = v;
    }

    public ClearanceDepartment getDepartment() {
        return department;
    }

    public void setDepartment(ClearanceDepartment v) {
        this.department = v;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String v) {
        this.itemName = v;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int v) {
        this.sortOrder = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
