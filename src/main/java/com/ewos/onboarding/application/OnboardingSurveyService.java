package com.ewos.onboarding.application;

import com.ewos.onboarding.api.OnboardingMapper;
import com.ewos.onboarding.api.dto.OnboardingSurveyResponse;
import com.ewos.onboarding.api.dto.SubmitOnboardingSurveyRequest;
import com.ewos.onboarding.domain.OnboardingPlan;
import com.ewos.onboarding.domain.OnboardingSurvey;
import com.ewos.onboarding.domain.events.OnboardingEvent;
import com.ewos.onboarding.domain.events.OnboardingEventType;
import com.ewos.onboarding.infrastructure.persistence.OnboardingSurveyRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OnboardingSurveyService {

    private final OnboardingSurveyRepository surveys;
    private final OnboardingPlanService plans;
    private final OnboardingMapper mapper;
    private final ApplicationEventPublisher events;

    public OnboardingSurveyService(
            OnboardingSurveyRepository surveys,
            OnboardingPlanService plans,
            OnboardingMapper mapper,
            ApplicationEventPublisher events) {
        this.surveys = surveys;
        this.plans = plans;
        this.mapper = mapper;
        this.events = events;
    }

    public OnboardingSurveyResponse submit(
            UUID tenantId, UUID planId, SubmitOnboardingSurveyRequest req) {
        OnboardingPlan plan = plans.require(tenantId, planId);
        OnboardingSurvey existing =
                surveys.findByTenantIdAndPlanIdAndSurveyType(tenantId, planId, req.surveyType())
                        .orElse(null);
        OnboardingSurvey s = existing == null ? new OnboardingSurvey() : existing;
        boolean isUpdate = existing != null;
        if (!isUpdate) {
            s.setTenantId(tenantId);
            s.setPlan(plan);
            s.setSurveyType(req.surveyType());
        }
        s.setResponsesJson(req.responsesJson());
        s.setOverallRating(req.overallRating());
        s.setComments(req.comments());
        s.setSubmittedAt(Instant.now());
        s = surveys.save(s);
        events.publishEvent(
                new OnboardingEvent(
                        OnboardingEventType.SURVEY_SUBMITTED,
                        tenantId,
                        plan.getCompanyId(),
                        plan.getId(),
                        plan.getEmployee() == null ? null : plan.getEmployee().getId(),
                        null,
                        s.getId(),
                        req.surveyType().name(),
                        OnboardingSecurity.currentActor(),
                        Instant.now()));
        return mapper.toResponse(s);
    }

    @Transactional(readOnly = true)
    public List<OnboardingSurveyResponse> listForPlan(UUID tenantId, UUID planId) {
        plans.require(tenantId, planId);
        return surveys.findAllByTenantIdAndPlanIdOrderBySubmittedAtAsc(tenantId, planId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OnboardingSurveyResponse getFor(
            UUID tenantId, UUID planId, com.ewos.onboarding.domain.OnboardingSurveyType type) {
        plans.require(tenantId, planId);
        return surveys.findByTenantIdAndPlanIdAndSurveyType(tenantId, planId, type)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Survey not found"));
    }
}
