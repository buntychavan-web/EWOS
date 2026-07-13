package com.ewos.person.domain;

import com.ewos.common.persistence.AuditableEntity;
import com.ewos.company.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Person is the permanent identity in EWOS. A person is NOT an employee — Employment records live
 * separately (Sprint 8.2) and reference this person id. Profile data lives effective-dated in
 * {@link PersonVersion}.
 */
@Entity
@Table(name = "persons")
@SQLDelete(sql = "UPDATE persons SET deleted_at = NOW(), version = version + 1 WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Person extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** Immutable, human-friendly identifier (e.g. "P000000001"). Never reused. */
    @Column(name = "group_person_id", nullable = false, length = 20, updatable = false)
    private String groupPersonId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public String getGroupPersonId() {
        return groupPersonId;
    }

    public void setGroupPersonId(String groupPersonId) {
        this.groupPersonId = groupPersonId;
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

    public long getVersion() {
        return version;
    }
}
