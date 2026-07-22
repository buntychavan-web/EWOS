package com.ewos.performance.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Weighted section within an appraisal template. */
@Entity
@Table(name = "appraisal_template_sections")
@SQLDelete(
        sql =
                "UPDATE appraisal_template_sections SET deleted_at = NOW() WHERE id = ? AND"
                        + " version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class AppraisalTemplateSection extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private AppraisalTemplate template;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "weightage", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightage = new BigDecimal("100.00");

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

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

    public AppraisalTemplate getTemplate() {
        return template;
    }

    public void setTemplate(AppraisalTemplate v) {
        this.template = v;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        this.code = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public BigDecimal getWeightage() {
        return weightage;
    }

    public void setWeightage(BigDecimal v) {
        this.weightage = v;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int v) {
        this.displayOrder = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
