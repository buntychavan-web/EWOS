package com.ewos.integration.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Which {@link IntegrationAdapterType} and connection settings a company uses for a given exchange
 * type. One active configuration per (company, exchangeType) at a time — {@code config_json} is
 * transport-specific (endpoint URL for REST, host/port/path for SFTP, output directory for
 * CSV/EXCEL/FILE_UPLOAD), kept as opaque metadata so adding a new field never requires a schema
 * change, matching the platform's existing zero-hardcoded-vocabulary convention.
 */
@Entity
@Table(name = "integration_configurations")
@SQLDelete(
        sql =
                "UPDATE integration_configurations SET deleted_at = NOW() WHERE id = ? AND"
                        + " version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class IntegrationConfiguration extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "exchange_type", nullable = false, length = 64)
    private String exchangeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "adapter_type", nullable = false, length = 32)
    private IntegrationAdapterType adapterType;

    @Column(name = "config_json", nullable = false, length = 4000)
    private String configJson;

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

    public String getExchangeType() {
        return exchangeType;
    }

    public void setExchangeType(String exchangeType) {
        this.exchangeType = exchangeType;
    }

    public IntegrationAdapterType getAdapterType() {
        return adapterType;
    }

    public void setAdapterType(IntegrationAdapterType adapterType) {
        this.adapterType = adapterType;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
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
