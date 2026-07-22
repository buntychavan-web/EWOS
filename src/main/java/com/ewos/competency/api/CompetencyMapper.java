package com.ewos.competency.api;

import com.ewos.competency.api.dto.ActionResponse;
import com.ewos.competency.api.dto.AssessmentResponse;
import com.ewos.competency.api.dto.CompetencyResponse;
import com.ewos.competency.api.dto.EmployeeCompetencyResponse;
import com.ewos.competency.api.dto.PlanResponse;
import com.ewos.competency.api.dto.RoleCompetencyResponse;
import com.ewos.competency.domain.Competency;
import com.ewos.competency.domain.CompetencyAssessment;
import com.ewos.competency.domain.DevelopmentAction;
import com.ewos.competency.domain.DevelopmentPlan;
import com.ewos.competency.domain.EmployeeCompetency;
import com.ewos.competency.domain.RoleCompetency;
import org.springframework.stereotype.Component;

/** Reflection-free mapper for competency entities. */
@Component
public class CompetencyMapper {

    public CompetencyResponse toResponse(Competency c) {
        return new CompetencyResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getCode(),
                c.getName(),
                c.getDescription(),
                c.getCategory(),
                c.getScaleMin(),
                c.getScaleMax(),
                c.isActive());
    }

    public RoleCompetencyResponse toResponse(RoleCompetency r) {
        return new RoleCompetencyResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getOrgUnitId(),
                r.getDesignation(),
                r.getCompetency() == null ? null : r.getCompetency().getId(),
                r.getRequiredLevel(),
                r.getWeightage(),
                r.getNotes());
    }

    public EmployeeCompetencyResponse toResponse(EmployeeCompetency e) {
        return new EmployeeCompetencyResponse(
                e.getId(),
                e.getTenantId(),
                e.getCompanyId(),
                e.getEmployee() == null ? null : e.getEmployee().getId(),
                e.getCompetency() == null ? null : e.getCompetency().getId(),
                e.getCurrentLevel(),
                e.getTargetLevel(),
                e.getLastAssessedAt(),
                e.getNotes());
    }

    public AssessmentResponse toResponse(CompetencyAssessment a) {
        return new AssessmentResponse(
                a.getId(),
                a.getTenantId(),
                a.getCompanyId(),
                a.getEmployee() == null ? null : a.getEmployee().getId(),
                a.getCompetency() == null ? null : a.getCompetency().getId(),
                a.getAssessmentType(),
                a.getAssessedLevel(),
                a.getAssessedBy(),
                a.getAssessorName(),
                a.getComments(),
                a.getAssessedAt());
    }

    public PlanResponse toResponse(DevelopmentPlan p) {
        return new PlanResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getEmployee() == null ? null : p.getEmployee().getId(),
                p.getTitle(),
                p.getDescription(),
                p.getStartsOn(),
                p.getEndsOn(),
                p.getStatus(),
                p.getCompletedAt());
    }

    public ActionResponse toResponse(DevelopmentAction a) {
        return new ActionResponse(
                a.getId(),
                a.getPlan() == null ? null : a.getPlan().getId(),
                a.getCompetency() == null ? null : a.getCompetency().getId(),
                a.getAction(),
                a.getDueOn(),
                a.getCompletedAt(),
                a.isCompleted(),
                a.getNotes());
    }
}
