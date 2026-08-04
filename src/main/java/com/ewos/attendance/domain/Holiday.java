package com.ewos.attendance.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Sprint 24L item 3 — a non-working calendar day, tenant-wide ({@link #companyId} {@code null}) or
 * scoped to one company. Consumed by {@link AttendanceLopCalculator} so a holiday is never
 * misclassified as an unexplained absence; {@link #recurringAnnually} lets a fixed-date holiday
 * (e.g. a national day) be entered once and match every year rather than needing one row per year.
 */
@Entity
@Table(name = "holidays")
@SQLDelete(sql = "UPDATE holidays SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class Holiday extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", updatable = false)
    private UUID companyId;

    @Column(name = "holiday_date", nullable = false, updatable = false)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "recurring_annually", nullable = false, updatable = false)
    private boolean recurringAnnually;

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

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public void setHolidayDate(LocalDate holidayDate) {
        this.holidayDate = holidayDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRecurringAnnually() {
        return recurringAnnually;
    }

    public void setRecurringAnnually(boolean recurringAnnually) {
        this.recurringAnnually = recurringAnnually;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }

    /** True if this holiday (fixed-date or recurring) falls on {@code date}. */
    public boolean fallsOn(LocalDate date) {
        if (recurringAnnually) {
            return holidayDate.getMonthValue() == date.getMonthValue()
                    && holidayDate.getDayOfMonth() == date.getDayOfMonth();
        }
        return holidayDate.equals(date);
    }
}
