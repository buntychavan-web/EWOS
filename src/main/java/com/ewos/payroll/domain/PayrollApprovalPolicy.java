package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Company-wise Payroll Maker-Checker configuration (Sprint 24L item 1). At most one active policy
 * per company ({@code ux_payroll_approval_policies_company_alive}). A company with no active policy
 * behaves exactly as before this sprint — a completed run may be finalized directly (subject only
 * to the unconditional preparer-cannot-finalize-their-own-run rule enforced in {@code
 * PayrollRunService.finalizeRun}) — so adopting maker-checker is opt-in per company, but once
 * adopted it is a real, blocking gate, unlike the workflow-engine integration this replaces.
 */
@Entity
@Table(name = "payroll_approval_policies")
@SQLDelete(
        sql =
                "UPDATE payroll_approval_policies SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class PayrollApprovalPolicy extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(
            mappedBy = "policy",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("levelNumber ASC")
    private List<PayrollApprovalLevel> levels = new ArrayList<>();

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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<PayrollApprovalLevel> getLevels() {
        return Collections.unmodifiableList(levels);
    }

    /** Replaces the entire hierarchy — levels are always edited as a whole ordered list. */
    public void replaceLevels(List<PayrollApprovalLevel> newLevels) {
        levels.clear();
        for (PayrollApprovalLevel level : newLevels) {
            level.setPolicy(this);
            levels.add(level);
        }
        levels.sort(Comparator.comparingInt(PayrollApprovalLevel::getLevelNumber));
    }

    public int levelCount() {
        return levels.size();
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
