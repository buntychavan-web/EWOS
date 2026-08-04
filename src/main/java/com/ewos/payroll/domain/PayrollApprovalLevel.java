package com.ewos.payroll.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One rung of a {@link PayrollApprovalPolicy}'s hierarchy. {@code approverRoleCode} is resolved to
 * concrete approvers the same way {@code com.ewos.workflow.application.ApproverResolver} resolves a
 * plain role code — any user holding that role in the company may decide this level.
 */
@Entity
@Table(name = "payroll_approval_levels")
public class PayrollApprovalLevel extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false, updatable = false)
    private PayrollApprovalPolicy policy;

    @Column(name = "level_number", nullable = false)
    private int levelNumber;

    @Column(name = "approver_role_code", nullable = false, length = 128)
    private String approverRoleCode;

    @Column(name = "description", length = 512)
    private String description;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public PayrollApprovalPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(PayrollApprovalPolicy policy) {
        this.policy = policy;
    }

    public int getLevelNumber() {
        return levelNumber;
    }

    public void setLevelNumber(int levelNumber) {
        this.levelNumber = levelNumber;
    }

    public String getApproverRoleCode() {
        return approverRoleCode;
    }

    public void setApproverRoleCode(String approverRoleCode) {
        this.approverRoleCode = approverRoleCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
