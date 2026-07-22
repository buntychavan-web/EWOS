package com.ewos.learning.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Course in the training catalogue. */
@Entity
@Table(name = "training_courses")
@SQLDelete(sql = "UPDATE training_courses SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class TrainingCourse extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false, length = 16)
    private DeliveryMode deliveryMode;

    @Column(name = "provider", length = 256)
    private String provider;

    @Column(name = "duration_hours", precision = 6, scale = 2)
    private BigDecimal durationHours;

    @Column(name = "cost", precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "certification_offered", nullable = false)
    private boolean certificationOffered;

    @Column(name = "certification_valid_days")
    private Integer certificationValidDays;

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

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode v) {
        this.deliveryMode = v;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String v) {
        this.provider = v;
    }

    public BigDecimal getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(BigDecimal v) {
        this.durationHours = v;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal v) {
        this.cost = v;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String v) {
        this.currency = v;
    }

    public boolean isCertificationOffered() {
        return certificationOffered;
    }

    public void setCertificationOffered(boolean v) {
        this.certificationOffered = v;
    }

    public Integer getCertificationValidDays() {
        return certificationValidDays;
    }

    public void setCertificationValidDays(Integer v) {
        this.certificationValidDays = v;
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
