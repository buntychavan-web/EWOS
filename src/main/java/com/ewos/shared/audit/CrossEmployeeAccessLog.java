package com.ewos.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Sprint 27A — one immutable record of an actor employee reading or acting on a target employee's
 * data (PRD §14, audit finding 3.3/3.6). Deliberately does <strong>not</strong> extend {@code
 * AuditableEntity}: that base class is for mutable, soft-deletable business entities with {@code
 * created_by}/{@code updated_by} auditing, whereas a row here is written once, never updated, and
 * only ever hard-deleted by the retention purge job ({@code PurgeJob.purgeMssAccessLogs}) — soft
 * delete would defeat the point of an access log.
 *
 * <p>{@code action} is a free-form identifier chosen by the calling module (e.g. {@code
 * "TEAM_DRILL_DOWN"}, {@code "APPROVAL_ACTION"}), not an enum, for the same reason {@link
 * com.ewos.employee.domain.MssFieldVisibilityConfig#getFieldName()} isn't: the set of MSS actions
 * this log records grows as later sub-sprints add more MSS reads, and this table must not require a
 * migration each time one is added.
 */
@Entity
@Table(name = "mss_access_log")
public class CrossEmployeeAccessLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "actor_employee_id", nullable = false, updatable = false)
    private UUID actorEmployeeId;

    @Column(name = "target_employee_id", nullable = false, updatable = false)
    private UUID targetEmployeeId;

    @Column(name = "action", nullable = false, length = 100, updatable = false)
    private String action;

    @Column(name = "granted", nullable = false, updatable = false)
    private boolean granted;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "correlation_id", length = 100, updatable = false)
    private String correlationId;

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getActorEmployeeId() {
        return actorEmployeeId;
    }

    public void setActorEmployeeId(UUID actorEmployeeId) {
        this.actorEmployeeId = actorEmployeeId;
    }

    public UUID getTargetEmployeeId() {
        return targetEmployeeId;
    }

    public void setTargetEmployeeId(UUID targetEmployeeId) {
        this.targetEmployeeId = targetEmployeeId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public boolean isGranted() {
        return granted;
    }

    public void setGranted(boolean granted) {
        this.granted = granted;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
