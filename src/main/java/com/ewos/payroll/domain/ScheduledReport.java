package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Recurrence declaration for a payroll report. Actual dispatch (evaluating the cron expression,
 * rendering the report, mailing / uploading it) is handled by an operator-supplied scheduler
 * plugin; this entity stores only the intent and the last-run marker.
 */
@Entity
@Table(name = "scheduled_reports")
@SQLDelete(sql = "UPDATE scheduled_reports SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class ScheduledReport extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "report_code", nullable = false, length = 64)
    private String reportCode;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "cron_expression", nullable = false, length = 128)
    private String cronExpression;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 32)
    private ScheduledReportFormat format = ScheduledReportFormat.CSV;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json", nullable = false, columnDefinition = "jsonb")
    private String parametersJson = "{}";

    @Column(name = "recipients", length = 2048)
    private String recipients;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_run_status", length = 32)
    private String lastRunStatus;

    @Column(name = "last_run_message", length = 2048)
    private String lastRunMessage;

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

    public String getReportCode() {
        return reportCode;
    }

    public void setReportCode(String v) {
        this.reportCode = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String v) {
        this.cronExpression = v;
    }

    public ScheduledReportFormat getFormat() {
        return format;
    }

    public void setFormat(ScheduledReportFormat v) {
        this.format = v;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String v) {
        this.parametersJson = (v == null || v.isBlank()) ? "{}" : v;
    }

    public String getRecipients() {
        return recipients;
    }

    public void setRecipients(String v) {
        this.recipients = v;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean v) {
        this.active = v;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant v) {
        this.lastRunAt = v;
    }

    public String getLastRunStatus() {
        return lastRunStatus;
    }

    public void setLastRunStatus(String v) {
        this.lastRunStatus = v;
    }

    public String getLastRunMessage() {
        return lastRunMessage;
    }

    public void setLastRunMessage(String v) {
        this.lastRunMessage = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
