package com.ewos.workflow.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One node of a workflow definition's state graph. Exactly one state per definition has {@code
 * isInitial = true} (enforced by a partial unique index in Flyway V11). Terminal states carry
 * {@code isTerminal = true} and do not have outgoing transitions.
 */
@Entity
@Table(name = "workflow_states")
public class WorkflowState extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private WorkflowDefinition definition;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    @Column(name = "is_terminal", nullable = false)
    private boolean terminal;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "sla_hours")
    private Integer slaHours;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public WorkflowDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(WorkflowDefinition definition) {
        this.definition = definition;
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

    public boolean isInitial() {
        return initial;
    }

    public void setInitial(boolean initial) {
        this.initial = initial;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public void setTerminal(boolean terminal) {
        this.terminal = terminal;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getSlaHours() {
        return slaHours;
    }

    public void setSlaHours(Integer slaHours) {
        this.slaHours = slaHours;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
