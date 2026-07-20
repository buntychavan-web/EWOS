package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-employee override of a specific {@link PayComponent} on an {@link EmployeeCompensation}. Uses
 * the same shape as the component's default — either a fixed amount or a percentage of basic — but
 * lets the compensation record deviate from the catalogue default.
 */
@Entity
@Table(name = "employee_compensation_lines")
public class EmployeeCompensationLine extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compensation_id", nullable = false, updatable = false)
    private EmployeeCompensation compensation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pay_component_id", nullable = false, updatable = false)
    private PayComponent payComponent;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal percentage = BigDecimal.ZERO;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public EmployeeCompensation getCompensation() {
        return compensation;
    }

    public void setCompensation(EmployeeCompensation compensation) {
        this.compensation = compensation;
    }

    public PayComponent getPayComponent() {
        return payComponent;
    }

    public void setPayComponent(PayComponent payComponent) {
        this.payComponent = payComponent;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
