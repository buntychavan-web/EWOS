package com.ewos.leave.application;

import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.workflow.application.WorkflowVariableResolver;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Exposes leave-request fields to workflow guard expressions (Sprint 4 auto-approval rules), e.g. a
 * tenant can configure an auto transition guarded by {@code #daysRequested <= 2} to skip the human
 * approval step for short leave. Read-only — the workflow engine never writes back through this
 * port.
 */
@Component
public class LeaveRequestWorkflowVariableResolver implements WorkflowVariableResolver {

    private final LeaveRequestRepository requests;

    public LeaveRequestWorkflowVariableResolver(LeaveRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    public boolean supports(String subjectType) {
        return LeaveRequestService.SUBJECT_TYPE.equalsIgnoreCase(subjectType);
    }

    @Override
    public Map<String, Object> resolve(UUID subjectId) {
        return requests.findById(subjectId)
                .map(
                        r ->
                                Map.<String, Object>of(
                                        "daysRequested", r.getDaysRequested(),
                                        "leaveTypeCode", r.getLeaveType().getCode()))
                .orElseGet(Map::of);
    }
}
