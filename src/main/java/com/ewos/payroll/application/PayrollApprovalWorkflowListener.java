package com.ewos.payroll.application;

import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import com.ewos.workflow.api.dto.AssignTaskRequest;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import com.ewos.workflow.application.WorkflowTaskService;
import com.ewos.workflow.domain.WorkflowActorType;
import com.ewos.workflow.domain.WorkflowDefinition;
import com.ewos.workflow.domain.events.WorkflowEvent;
import com.ewos.workflow.domain.events.WorkflowEventType;
import com.ewos.workflow.infrastructure.persistence.WorkflowDefinitionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 14.3 §3 — ties Payroll to the Client Approval Workflow entirely through the two modules'
 * existing domain events. Zero changes to {@link PayrollRunService} or the workflow engine:
 *
 * <ul>
 *   <li>On {@code RUN_COMPLETED} (published by the unmodified {@link PayrollRunService}), starts an
 *       instance of the {@code PAYROLL_CLIENT_APPROVAL} workflow definition (seeded by migration
 *       V36) against the run, then assigns the resulting {@code PENDING_REVIEW} task to the {@code
 *       CLIENT_ADMIN} role — the same role the definition's APPROVE/REJECT transitions require — so
 *       any client-side approver can claim and complete it through the existing, generic Workflow
 *       Task API.
 *   <li>On {@code INSTANCE_COMPLETED} for that same workflow reaching its {@code APPROVED} state,
 *       calls the existing, unmodified {@link PayrollRunService#finalizeRun} — the same action a
 *       human would take via {@code POST /payroll/runs/{id}/finalize}. A run whose workflow reaches
 *       {@code REJECTED} is left as-is for manual handling; nothing here forces a decision on the
 *       payroll engine.
 * </ul>
 *
 * <p>"Reaches VALIDATED status" in the Sprint 14 design maps onto the existing {@code
 * RUN_COMPLETED} checkpoint — the run's processing-finished/awaiting-review state that Sprint
 * 14.2's Provider Dashboard already surfaces as "Pending Approvals". No new {@code
 * PayrollRunStatus} value was added; doing so would have meant modifying the frozen Payroll
 * engine's lifecycle enum.
 */
@Component
public class PayrollApprovalWorkflowListener {

    private static final Logger LOG =
            LoggerFactory.getLogger(PayrollApprovalWorkflowListener.class);
    private static final String DEFINITION_CODE = "PAYROLL_CLIENT_APPROVAL";
    private static final String SUBJECT_TYPE = "PAYROLL_RUN";
    private static final String APPROVED_STATE_CODE = "APPROVED";
    private static final String APPROVER_ROLE_CODE = "CLIENT_ADMIN";

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowInstanceService workflowInstances;
    private final WorkflowTaskService workflowTasks;
    private final PayrollRunService runs;

    public PayrollApprovalWorkflowListener(
            WorkflowDefinitionRepository definitions,
            WorkflowInstanceService workflowInstances,
            WorkflowTaskService workflowTasks,
            PayrollRunService runs) {
        this.definitions = definitions;
        this.workflowInstances = workflowInstances;
        this.workflowTasks = workflowTasks;
        this.runs = runs;
    }

    @EventListener
    @Transactional
    public void onPayrollEvent(PayrollEvent event) {
        if (event.eventType() != PayrollEventType.RUN_COMPLETED) {
            return;
        }
        List<WorkflowDefinition> active =
                definitions.findActiveByTenantAndCode(event.tenantId(), DEFINITION_CODE);
        if (active.isEmpty()) {
            LOG.warn(
                    "No active '{}' workflow definition for tenant={}; skipping client approval"
                            + " for run={}",
                    DEFINITION_CODE,
                    event.tenantId(),
                    event.payrollRunId());
            return;
        }
        WorkflowDefinition definition = active.get(0);
        try {
            WorkflowInstanceResponse instance =
                    workflowInstances.start(
                            new StartInstanceRequest(
                                    event.tenantId(),
                                    event.companyId(),
                                    definition.getId(),
                                    SUBJECT_TYPE,
                                    event.payrollRunId(),
                                    SUBJECT_TYPE + ":" + event.payrollRunId()));
            workflowTasks.assign(
                    event.tenantId(),
                    instance.id(),
                    new AssignTaskRequest(
                            WorkflowActorType.ROLE,
                            null,
                            APPROVER_ROLE_CODE,
                            null,
                            null,
                            "Payroll run awaiting client approval"));
        } catch (RuntimeException e) {
            LOG.warn(
                    "Failed to start client approval workflow for run={} tenant={}: {}",
                    event.payrollRunId(),
                    event.tenantId(),
                    e.getMessage());
        }
    }

    @EventListener
    @Transactional
    public void onWorkflowEvent(WorkflowEvent event) {
        if (event.eventType() != WorkflowEventType.INSTANCE_COMPLETED
                || !SUBJECT_TYPE.equals(event.subjectType())
                || !APPROVED_STATE_CODE.equalsIgnoreCase(event.toStateCode())) {
            return;
        }
        try {
            runs.finalizeRun(event.tenantId(), event.subjectId());
        } catch (RuntimeException e) {
            LOG.warn(
                    "Client approval for run={} tenant={} completed but finalizeRun failed: {}",
                    event.subjectId(),
                    event.tenantId(),
                    e.getMessage());
        }
    }
}
