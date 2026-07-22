package com.ewos.performance.api;

import com.ewos.employee.domain.Employee;
import com.ewos.performance.api.dto.AppraisalReportRowResponse;
import com.ewos.performance.api.dto.AppraisalResponse;
import com.ewos.performance.api.dto.AppraisalTemplateResponse;
import com.ewos.performance.api.dto.CalibrationSessionResponse;
import com.ewos.performance.api.dto.PerformanceCycleResponse;
import com.ewos.performance.api.dto.TemplateSectionResponse;
import com.ewos.performance.domain.Appraisal;
import com.ewos.performance.domain.AppraisalTemplate;
import com.ewos.performance.domain.AppraisalTemplateSection;
import com.ewos.performance.domain.CalibrationSession;
import com.ewos.performance.domain.PerformanceCycle;
import org.springframework.stereotype.Component;

/** Reflection-free mapper for performance entities. */
@Component
public class PerformanceMapper {

    public PerformanceCycleResponse toResponse(PerformanceCycle c) {
        return new PerformanceCycleResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getCode(),
                c.getName(),
                c.getDescription(),
                c.getPeriodStart(),
                c.getPeriodEnd(),
                c.getStatus(),
                c.getSelfAssessmentDue(),
                c.getManagerAssessmentDue(),
                c.getReviewerAssessmentDue(),
                c.getCalibrationDue(),
                c.isBellCurveEnabled(),
                c.getBellCurveConfigJson());
    }

    public AppraisalTemplateResponse toResponse(AppraisalTemplate t) {
        return new AppraisalTemplateResponse(
                t.getId(),
                t.getTenantId(),
                t.getCompanyId(),
                t.getCode(),
                t.getName(),
                t.getDescription(),
                t.getRatingScaleMin(),
                t.getRatingScaleMax(),
                t.isActive());
    }

    public TemplateSectionResponse toResponse(AppraisalTemplateSection s) {
        return new TemplateSectionResponse(
                s.getId(),
                s.getTemplate() == null ? null : s.getTemplate().getId(),
                s.getCode(),
                s.getName(),
                s.getDescription(),
                s.getWeightage(),
                s.getDisplayOrder());
    }

    public AppraisalResponse toResponse(Appraisal a) {
        return new AppraisalResponse(
                a.getId(),
                a.getTenantId(),
                a.getCompanyId(),
                a.getCycle() == null ? null : a.getCycle().getId(),
                a.getTemplate() == null ? null : a.getTemplate().getId(),
                a.getEmployee() == null ? null : a.getEmployee().getId(),
                a.getManagerEmployee() == null ? null : a.getManagerEmployee().getId(),
                a.getReviewerEmployee() == null ? null : a.getReviewerEmployee().getId(),
                a.getStatus(),
                a.getSelfRating(),
                a.getSelfComments(),
                a.getSelfSubmittedAt(),
                a.getManagerRating(),
                a.getManagerComments(),
                a.getManagerSubmittedAt(),
                a.getReviewerRating(),
                a.getReviewerComments(),
                a.getReviewerSubmittedAt(),
                a.getCalibratedRating(),
                a.getCalibrationNotes(),
                a.getCalibratedAt(),
                a.getCalibratedBy(),
                a.getFinalRating(),
                a.getFinalBand(),
                a.getIncrementRecommendation(),
                a.getIncrementPercent(),
                a.getIncrementNotes(),
                a.getPromotionRecommendation(),
                a.getPromotionNotes(),
                a.getApprovalWorkflowInstanceId());
    }

    public CalibrationSessionResponse toResponse(CalibrationSession s) {
        return new CalibrationSessionResponse(
                s.getId(),
                s.getTenantId(),
                s.getCompanyId(),
                s.getCycle() == null ? null : s.getCycle().getId(),
                s.getName(),
                s.getScheduledAt(),
                s.getStatus(),
                s.getFacilitatorId(),
                s.getNotes(),
                s.getCompletedAt());
    }

    public AppraisalReportRowResponse toReportRow(Appraisal a) {
        Employee e = a.getEmployee();
        String number = e == null ? null : e.getEmployeeNumber();
        String name = e == null ? null : displayName(e);
        return new AppraisalReportRowResponse(
                a.getId(),
                e == null ? null : e.getId(),
                number,
                name,
                a.getStatus(),
                a.getSelfRating(),
                a.getManagerRating(),
                a.getReviewerRating(),
                a.getCalibratedRating(),
                a.getFinalRating(),
                a.getFinalBand(),
                a.getIncrementRecommendation(),
                a.getPromotionRecommendation());
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
