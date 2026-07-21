package com.ewos.payroll.domain;

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
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line item on a {@link Payslip}. Component identifier is retained for join-back, but code /
 * name / kind / calculation type / amount are snapshotted so that even if the catalogue changes the
 * payslip remains reproducible verbatim.
 */
@Entity
@Table(name = "payslip_lines")
public class PayslipLine extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payslip_id", nullable = false, updatable = false)
    private Payslip payslip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pay_component_id", nullable = false, updatable = false)
    private PayComponent payComponent;

    @Column(name = "component_code_snapshot", nullable = false, length = 64)
    private String componentCodeSnapshot;

    @Column(name = "component_name_snapshot", nullable = false, length = 128)
    private String componentNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private PayComponentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 32)
    private PayComponentCalculationType calculationType;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "percentage_applied", nullable = false, precision = 7, scale = 4)
    private BigDecimal percentageApplied = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Payslip getPayslip() {
        return payslip;
    }

    public void setPayslip(Payslip payslip) {
        this.payslip = payslip;
    }

    public PayComponent getPayComponent() {
        return payComponent;
    }

    public void setPayComponent(PayComponent payComponent) {
        this.payComponent = payComponent;
    }

    public String getComponentCodeSnapshot() {
        return componentCodeSnapshot;
    }

    public void setComponentCodeSnapshot(String v) {
        this.componentCodeSnapshot = v;
    }

    public String getComponentNameSnapshot() {
        return componentNameSnapshot;
    }

    public void setComponentNameSnapshot(String v) {
        this.componentNameSnapshot = v;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getPercentageApplied() {
        return percentageApplied;
    }

    public void setPercentageApplied(BigDecimal v) {
        this.percentageApplied = v;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
