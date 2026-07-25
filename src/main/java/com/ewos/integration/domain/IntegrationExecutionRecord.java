package com.ewos.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Append-only record of one {@link IntegrationAdapter} execution attempt — same convention as
 * {@code WorkflowHistory} / {@code DataExchangeHistory}: never soft-deleted, never updated after
 * insert. Doubles as the Sprint 14.4 "Enhanced Audit & Operational Tracking" trail for the
 * integration layer and as the raw data for the Integration Monitoring Dashboard.
 */
@Entity
@Table(name = "integration_execution_records")
public class IntegrationExecutionRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "data_exchange_record_id", nullable = false, updatable = false)
    private UUID dataExchangeRecordId;

    @Column(name = "configuration_id", updatable = false)
    private UUID configurationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adapter_type", length = 32, updatable = false)
    private IntegrationAdapterType adapterType;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16, updatable = false)
    private IntegrationExecutionOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_classification", length = 32, updatable = false)
    private ErrorClassification errorClassification;

    @Column(name = "error_message", length = 2048, updatable = false)
    private String errorMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    @Column(name = "duration_ms", updatable = false)
    private Long durationMs;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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

    public UUID getDataExchangeRecordId() {
        return dataExchangeRecordId;
    }

    public void setDataExchangeRecordId(UUID dataExchangeRecordId) {
        this.dataExchangeRecordId = dataExchangeRecordId;
    }

    public UUID getConfigurationId() {
        return configurationId;
    }

    public void setConfigurationId(UUID configurationId) {
        this.configurationId = configurationId;
    }

    public IntegrationAdapterType getAdapterType() {
        return adapterType;
    }

    public void setAdapterType(IntegrationAdapterType adapterType) {
        this.adapterType = adapterType;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public IntegrationExecutionOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(IntegrationExecutionOutcome outcome) {
        this.outcome = outcome;
    }

    public ErrorClassification getErrorClassification() {
        return errorClassification;
    }

    public void setErrorClassification(ErrorClassification errorClassification) {
        this.errorClassification = errorClassification;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }
}
