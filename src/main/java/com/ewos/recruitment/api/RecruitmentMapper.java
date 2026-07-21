package com.ewos.recruitment.api;

import com.ewos.employee.domain.Employee;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.recruitment.api.dto.JobPositionResponse;
import com.ewos.recruitment.api.dto.JobRequisitionResponse;
import com.ewos.recruitment.domain.JobPosition;
import com.ewos.recruitment.domain.JobRequisition;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reflection-free mapper: domain entity → response DTO. */
@Component
public class RecruitmentMapper {

    public JobPositionResponse toResponse(JobPosition p) {
        return new JobPositionResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getCode(),
                p.getTitle(),
                p.getDescription(),
                idOf(p.getDepartmentOrgUnit()),
                p.getLocation(),
                p.getEmploymentType(),
                p.getGrade(),
                p.getSalaryCurrency(),
                p.getSalaryMin(),
                p.getSalaryMax(),
                p.isActive(),
                p.getVersionNo());
    }

    public JobRequisitionResponse toResponse(JobRequisition r) {
        return new JobRequisitionResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getRequisitionNumber(),
                r.getJobPosition() == null ? null : r.getJobPosition().getId(),
                r.getTitle(),
                idOf(r.getDepartmentOrgUnit()),
                r.getLocation(),
                r.getEmploymentType(),
                r.getHeadcount(),
                r.getFilledCount(),
                r.getPriority(),
                r.getJustification(),
                idOf(r.getHiringManager()),
                idOf(r.getRecruiter()),
                r.getTargetStartDate(),
                r.getBudgetCurrency(),
                r.getBudgetAmount(),
                r.getStatus(),
                r.getWorkflowInstanceId(),
                r.getSubmittedAt(),
                r.getDecidedAt(),
                r.getDecidedBy(),
                r.getDecisionNotes(),
                r.getOpenedAt(),
                r.getClosedAt(),
                r.getClosedReason(),
                r.getVersionNo());
    }

    private UUID idOf(OrganizationUnit u) {
        return u == null ? null : u.getId();
    }

    private UUID idOf(Employee e) {
        return e == null ? null : e.getId();
    }
}
