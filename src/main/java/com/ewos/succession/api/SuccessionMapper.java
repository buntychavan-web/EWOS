package com.ewos.succession.api;

import com.ewos.succession.api.dto.CandidateResponse;
import com.ewos.succession.api.dto.CareerPathResponse;
import com.ewos.succession.api.dto.EligibilityResponse;
import com.ewos.succession.api.dto.PlanResponse;
import com.ewos.succession.api.dto.PoolMemberResponse;
import com.ewos.succession.api.dto.PoolResponse;
import com.ewos.succession.api.dto.ReadinessResponse;
import com.ewos.succession.domain.CareerPath;
import com.ewos.succession.domain.PromotionEligibility;
import com.ewos.succession.domain.ReadinessAssessment;
import com.ewos.succession.domain.SuccessorCandidate;
import com.ewos.succession.domain.SuccessorPlan;
import com.ewos.succession.domain.TalentPool;
import com.ewos.succession.domain.TalentPoolMember;
import org.springframework.stereotype.Component;

/** Reflection-free mapper for succession entities. */
@Component
public class SuccessionMapper {

    public CareerPathResponse toResponse(CareerPath c) {
        return new CareerPathResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getCode(),
                c.getName(),
                c.getDescription(),
                c.getFromDesignation(),
                c.getToDesignation(),
                c.getMinTenureMonths(),
                c.isActive());
    }

    public EligibilityResponse toResponse(PromotionEligibility e) {
        return new EligibilityResponse(
                e.getId(),
                e.getTenantId(),
                e.getCompanyId(),
                e.getEmployee() == null ? null : e.getEmployee().getId(),
                e.getCareerPath() == null ? null : e.getCareerPath().getId(),
                e.isEligible(),
                e.getTenureMonths(),
                e.getLastRating(),
                e.getCompetencyGap(),
                e.getNotes(),
                e.getAssessedAt(),
                e.getAssessedBy());
    }

    public PoolResponse toResponse(TalentPool p) {
        return new PoolResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getTier(),
                p.isActive());
    }

    public PoolMemberResponse toResponse(TalentPoolMember m) {
        return new PoolMemberResponse(
                m.getId(),
                m.getPool() == null ? null : m.getPool().getId(),
                m.getEmployee() == null ? null : m.getEmployee().getId(),
                m.getAddedAt(),
                m.getAddedBy(),
                m.getRemovedAt(),
                m.getNotes());
    }

    public PlanResponse toResponse(SuccessorPlan p) {
        return new PlanResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getPositionTitle(),
                p.getIncumbent() == null ? null : p.getIncumbent().getId(),
                p.getOrgUnitId(),
                p.getNotes());
    }

    public CandidateResponse toResponse(SuccessorCandidate c) {
        return new CandidateResponse(
                c.getId(),
                c.getPlan() == null ? null : c.getPlan().getId(),
                c.getEmployee() == null ? null : c.getEmployee().getId(),
                c.getPriority(),
                c.getReadiness(),
                c.getNotes());
    }

    public ReadinessResponse toResponse(ReadinessAssessment r) {
        return new ReadinessResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getEmployee() == null ? null : r.getEmployee().getId(),
                r.getPerformanceScore(),
                r.getPotentialScore(),
                r.getTier(),
                r.getReadiness(),
                r.getNotes(),
                r.getAssessedAt(),
                r.getAssessedBy());
    }
}
