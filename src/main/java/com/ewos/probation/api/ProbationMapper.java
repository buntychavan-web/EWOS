package com.ewos.probation.api;

import com.ewos.employee.domain.Employee;
import com.ewos.probation.api.dto.ProbationPolicyResponse;
import com.ewos.probation.api.dto.ProbationRecordResponse;
import com.ewos.probation.api.dto.ProbationReportRowResponse;
import com.ewos.probation.domain.ProbationPolicy;
import com.ewos.probation.domain.ProbationRecord;
import org.springframework.stereotype.Component;

/** Reflection-free mapper for probation entities. */
@Component
public class ProbationMapper {

    public ProbationPolicyResponse toResponse(ProbationPolicy p) {
        return new ProbationPolicyResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getDefaultPeriodDays(),
                p.getMaxExtensionDays(),
                p.isAllowEarlyConfirm(),
                p.isActive());
    }

    public ProbationRecordResponse toResponse(ProbationRecord r) {
        return new ProbationRecordResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getEmployee() == null ? null : r.getEmployee().getId(),
                r.getPolicy() == null ? null : r.getPolicy().getId(),
                r.getPeriodStart(),
                r.getPeriodEnd(),
                r.getExtendedEnd(),
                r.effectiveEnd(),
                r.getExtensionReason(),
                r.getStatus(),
                r.getManagerReviewNotes(),
                r.getManagerReviewAt(),
                r.getManagerReviewBy(),
                r.getHrRecommendation(),
                r.getHrRecommendationNotes(),
                r.getHrRecommendedAt(),
                r.getHrRecommendedBy(),
                r.getApprovalWorkflowInstanceId(),
                r.getConfirmedAt(),
                r.getConfirmedBy(),
                r.getConfirmationLetterUri(),
                r.getTerminatedAt(),
                r.getTerminatedBy(),
                r.getOutcomeNotes());
    }

    public ProbationReportRowResponse toReportRow(ProbationRecord r) {
        Employee e = r.getEmployee();
        String number = e == null ? null : e.getEmployeeNumber();
        String name = e == null ? null : displayName(e);
        return new ProbationReportRowResponse(
                r.getId(),
                e == null ? null : e.getId(),
                number,
                name,
                r.getPeriodStart(),
                r.getPeriodEnd(),
                r.effectiveEnd(),
                r.getStatus(),
                r.getHrRecommendation());
    }

    private String displayName(Employee e) {
        String first = safe(e.getFirstName());
        String last = safe(e.getLastName());
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? null : combined;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
