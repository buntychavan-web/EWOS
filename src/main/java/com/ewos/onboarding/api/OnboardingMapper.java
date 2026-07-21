package com.ewos.onboarding.api;

import com.ewos.onboarding.api.dto.OnboardingPlanResponse;
import com.ewos.onboarding.api.dto.OnboardingSurveyResponse;
import com.ewos.onboarding.api.dto.OnboardingTaskInstanceResponse;
import com.ewos.onboarding.api.dto.OnboardingTaskTemplateResponse;
import com.ewos.onboarding.domain.OnboardingPlan;
import com.ewos.onboarding.domain.OnboardingSurvey;
import com.ewos.onboarding.domain.OnboardingTaskInstance;
import com.ewos.onboarding.domain.OnboardingTaskTemplate;
import org.springframework.stereotype.Component;

/** Reflection-free onboarding mapper. */
@Component
public class OnboardingMapper {

    public OnboardingTaskTemplateResponse toResponse(OnboardingTaskTemplate t) {
        return new OnboardingTaskTemplateResponse(
                t.getId(),
                t.getTenantId(),
                t.getCompanyId(),
                t.getCode(),
                t.getName(),
                t.getDescription(),
                t.getTaskType(),
                t.getSortOrder(),
                t.isMandatory(),
                t.getDefaultOwner(),
                t.getDefaultSlaDays(),
                t.isActive(),
                t.getVersionNo());
    }

    public OnboardingPlanResponse toResponse(OnboardingPlan p) {
        return new OnboardingPlanResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getEmployee() == null ? null : p.getEmployee().getId(),
                p.getSourceOfferId(),
                p.getSourceChecklistId(),
                p.getJoiningDate(),
                p.getManagerEmployee() == null ? null : p.getManagerEmployee().getId(),
                p.getBuddyEmployee() == null ? null : p.getBuddyEmployee().getId(),
                p.getStatus(),
                p.getCompletionPercent(),
                p.getStartedAt(),
                p.getCompletedAt(),
                p.getCompletedBy(),
                p.getNotes(),
                p.getVersionNo());
    }

    public OnboardingTaskInstanceResponse toResponse(OnboardingTaskInstance t) {
        return new OnboardingTaskInstanceResponse(
                t.getId(),
                t.getPlan() == null ? null : t.getPlan().getId(),
                t.getTemplate() == null ? null : t.getTemplate().getId(),
                t.getName(),
                t.getTaskType(),
                t.getOwner(),
                t.getAssignedEmployee() == null ? null : t.getAssignedEmployee().getId(),
                t.isMandatory(),
                t.getSortOrder(),
                t.getStatus(),
                t.getDueDate(),
                t.getStartedAt(),
                t.getCompletedAt(),
                t.getCompletedBy(),
                t.getExternalRef(),
                t.getResultJson(),
                t.getNotes(),
                t.getVersionNo());
    }

    public OnboardingSurveyResponse toResponse(OnboardingSurvey s) {
        return new OnboardingSurveyResponse(
                s.getId(),
                s.getPlan() == null ? null : s.getPlan().getId(),
                s.getSurveyType(),
                s.getResponsesJson(),
                s.getOverallRating(),
                s.getComments(),
                s.getSubmittedAt(),
                s.getVersionNo());
    }
}
