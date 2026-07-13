package com.ewos.person.domain;

import com.ewos.common.persistence.AuditableEntity;
import com.ewos.company.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Tenant-scoped configuration row per duplicate-detection rule. The service loops enabled rules
 * ordered by weight and returns matched candidate persons.
 */
@Entity
@Table(name = "person_duplicate_rules")
public class PersonDuplicateRule extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_kind", nullable = false, length = 30)
    private DuplicateRuleKind ruleKind;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "weight", nullable = false)
    private int weight = 50;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public DuplicateRuleKind getRuleKind() {
        return ruleKind;
    }

    public void setRuleKind(DuplicateRuleKind ruleKind) {
        this.ruleKind = ruleKind;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public long getVersion() {
        return version;
    }
}
