package com.ewos.employee.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Append-only audit trail for link / unlink / provision actions on {@link Employee#getUserId()}.
 * Follows the same idiom as {@code LoginHistory} (identity), {@code WorkflowHistory} (workflow), and
 * {@code DataExchangeHistory} (dataexchange): extends {@link AuditableEntity} so {@code created_by} /
 * {@code created_at} capture "performed by" / "date-time" automatically via the platform's existing
 * audit framework, rather than a parallel one.
 */
@Entity
@Table(name = "employee_identity_link_history")
public class EmployeeIdentityLinkHistory extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20, updatable = false)
    private EmployeeIdentityLinkAction action;

    @Column(name = "previous_user_id", updatable = false)
    private UUID previousUserId;

    @Column(name = "new_user_id", updatable = false)
    private UUID newUserId;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public EmployeeIdentityLinkAction getAction() {
        return action;
    }

    public void setAction(EmployeeIdentityLinkAction action) {
        this.action = action;
    }

    public UUID getPreviousUserId() {
        return previousUserId;
    }

    public void setPreviousUserId(UUID previousUserId) {
        this.previousUserId = previousUserId;
    }

    public UUID getNewUserId() {
        return newUserId;
    }

    public void setNewUserId(UUID newUserId) {
        this.newUserId = newUserId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
