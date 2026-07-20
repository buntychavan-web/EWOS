package com.ewos.payroll.domain;

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

/**
 * Per-tenant metadata dictionary of pay components — BASIC, HRA, TRANSPORT_ALLOWANCE,
 * PROVIDENT_FUND, INCOME_TAX, etc. Kept as data, not enum, so tenants can add jurisdiction-specific
 * items without a schema change. Combines a {@link PayComponentKind} (earning vs deduction) with a
 * {@link PayComponentCalculationType} (fixed vs percentage of basic).
 */
@Entity
@Table(name = "pay_components")
@SQLDelete(sql = "UPDATE pay_components SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class PayComponent extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private PayComponentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 32)
    private PayComponentCalculationType calculationType;

    @Column(name = "default_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal defaultAmount = BigDecimal.ZERO;

    @Column(name = "default_percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal defaultPercentage = BigDecimal.ZERO;

    @Column(name = "taxable", nullable = false)
    private boolean taxable = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PayComponentKind getKind() {
        return kind;
    }

    public void setKind(PayComponentKind kind) {
        this.kind = kind;
    }

    public PayComponentCalculationType getCalculationType() {
        return calculationType;
    }

    public void setCalculationType(PayComponentCalculationType v) {
        this.calculationType = v;
    }

    public BigDecimal getDefaultAmount() {
        return defaultAmount;
    }

    public void setDefaultAmount(BigDecimal v) {
        this.defaultAmount = v;
    }

    public BigDecimal getDefaultPercentage() {
        return defaultPercentage;
    }

    public void setDefaultPercentage(BigDecimal v) {
        this.defaultPercentage = v;
    }

    public boolean isTaxable() {
        return taxable;
    }

    public void setTaxable(boolean taxable) {
        this.taxable = taxable;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
