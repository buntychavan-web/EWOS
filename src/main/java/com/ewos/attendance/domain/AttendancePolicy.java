package com.ewos.attendance.domain;

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
 * Per-tenant metadata describing attendance rules — standard hours, working days, grace window,
 * overtime multiplier, timesheet period length. Optional {@code companyId} lets a tenant override
 * the rule for a specific legal entity while keeping a tenant-wide fallback.
 *
 * <p>Working days are stored as a comma-separated list of ISO short codes (MON,TUE,...) rather than
 * a bitmask so the SQL is readable and future timezone-aware operations can join on the day name
 * without a decode step.
 */
@Entity
@Table(name = "attendance_policies")
@SQLDelete(
        sql = "UPDATE attendance_policies SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class AttendancePolicy extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "standard_hours_per_day", nullable = false, precision = 4, scale = 2)
    private BigDecimal standardHoursPerDay = new BigDecimal("8.00");

    @Column(name = "standard_hours_per_week", nullable = false, precision = 5, scale = 2)
    private BigDecimal standardHoursPerWeek = new BigDecimal("40.00");

    @Column(name = "working_days", nullable = false, length = 64)
    private String workingDays = "MON,TUE,WED,THU,FRI";

    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes = 5;

    @Column(name = "overtime_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal overtimeMultiplier = new BigDecimal("1.50");

    @Column(name = "period_length_days", nullable = false)
    private int periodLengthDays = 7;

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

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
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

    public BigDecimal getStandardHoursPerDay() {
        return standardHoursPerDay;
    }

    public void setStandardHoursPerDay(BigDecimal v) {
        this.standardHoursPerDay = v;
    }

    public BigDecimal getStandardHoursPerWeek() {
        return standardHoursPerWeek;
    }

    public void setStandardHoursPerWeek(BigDecimal v) {
        this.standardHoursPerWeek = v;
    }

    public String getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(String workingDays) {
        this.workingDays = workingDays;
    }

    public int getGraceMinutes() {
        return graceMinutes;
    }

    public void setGraceMinutes(int graceMinutes) {
        this.graceMinutes = graceMinutes;
    }

    public BigDecimal getOvertimeMultiplier() {
        return overtimeMultiplier;
    }

    public void setOvertimeMultiplier(BigDecimal v) {
        this.overtimeMultiplier = v;
    }

    public int getPeriodLengthDays() {
        return periodLengthDays;
    }

    public void setPeriodLengthDays(int periodLengthDays) {
        this.periodLengthDays = periodLengthDays;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
