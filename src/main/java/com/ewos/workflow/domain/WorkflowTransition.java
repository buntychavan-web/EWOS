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
 * A directed edge in a workflow definition's state graph. An action code identifies the transition;
 * {@code required_role} restricts which principal can invoke it; {@code auto = true} means the
 * engine will follow the transition without a human task.
 */
@Entity
@Table(name = "workflow_transitions")
public class WorkflowTransition extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private WorkflowDefinition definition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_state_id", nullable = false)
    private WorkflowState fromState;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_state_id", nullable = false)
    private WorkflowState toState;

    @Column(name = "action_code", nullable = false, length = 64)
    private String actionCode;

    @Column(name = "required_role", length = 64)
    private String requiredRole;

    @Column(name = "auto", nullable = false)
    private boolean auto;

    @Column(name = "guard_expression", length = 2048)
    private String guardExpression;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public WorkflowDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(WorkflowDefinition definition) {
        this.definition = definition;
    }

    public WorkflowState getFromState() {
        return fromState;
    }

    public void setFromState(WorkflowState fromState) {
        this.fromState = fromState;
    }

    public WorkflowState getToState() {
        return toState;
    }

    public void setToState(WorkflowState toState) {
        this.toState = toState;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(String requiredRole) {
        this.requiredRole = requiredRole;
    }

    public boolean isAuto() {
        return auto;
    }

    public void setAuto(boolean auto) {
        this.auto = auto;
    }

    public String getGuardExpression() {
        return guardExpression;
    }

    public void setGuardExpression(String guardExpression) {
        this.guardExpression = guardExpression;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
