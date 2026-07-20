package com.ewos.leave.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Per-tenant metadata dictionary of leave kinds — VACATION, SICK, PERSONAL, MATERNITY, BEREAVEMENT,
 * UNPAID, etc. Kept as data, not enum, so tenants can add jurisdiction-specific categories (e.g.
 * VOLUNTEER_DAY, WELLNESS_DAY) without a schema change.
 */
@Entity
@Table(name = "leave_types")
@SQLDelete(sql = "UPDATE leave_types SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class LeaveType extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "accrual_days_per_year", nullable = false, precision = 6, scale = 2)
    private BigDecimal accrualDaysPerYear = BigDecimal.ZERO;

    @Column(name = "max_balance_days", precision = 6, scale = 2)
    private BigDecimal maxBalanceDays;

    @Column(name = "carry_forward_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal carryForwardDays = BigDecimal.ZERO;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;

    @Column(name = "min_notice_days", nullable = false)
    private int minNoticeDays;

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

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public BigDecimal getAccrualDaysPerYear() {
        return accrualDaysPerYear;
    }

    public void setAccrualDaysPerYear(BigDecimal v) {
        this.accrualDaysPerYear = v;
    }

    public BigDecimal getMaxBalanceDays() {
        return maxBalanceDays;
    }

    public void setMaxBalanceDays(BigDecimal v) {
        this.maxBalanceDays = v;
    }

    public BigDecimal getCarryForwardDays() {
        return carryForwardDays;
    }

    public void setCarryForwardDays(BigDecimal v) {
        this.carryForwardDays = v;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public int getMinNoticeDays() {
        return minNoticeDays;
    }

    public void setMinNoticeDays(int minNoticeDays) {
        this.minNoticeDays = minNoticeDays;
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
