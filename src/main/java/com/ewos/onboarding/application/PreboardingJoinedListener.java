package com.ewos.onboarding.application;

import com.ewos.offer.domain.events.OfferEvent;
import com.ewos.offer.domain.events.OfferEventType;
import com.ewos.offer.domain.preboarding.PreboardingChecklist;
import com.ewos.offer.infrastructure.persistence.PreboardingChecklistRepository;
import com.ewos.onboarding.api.dto.CreateOnboardingPlanRequest;
import com.ewos.onboarding.domain.events.OnboardingEvent;
import com.ewos.onboarding.domain.events.OnboardingEventType;
import com.ewos.onboarding.infrastructure.persistence.OnboardingPlanRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ties T4 pre-boarding to T5 onboarding. When a checklist reaches {@code JOINED} and carries an
 * {@code employee_id}, this listener materialises an {@link
 * com.ewos.onboarding.domain.OnboardingPlan onboarding plan} for the freshly-created employee
 * record — one plan per employee, idempotent.
 */
@Component
public class PreboardingJoinedListener {

    private static final Logger LOG = LoggerFactory.getLogger(PreboardingJoinedListener.class);

    private final OnboardingPlanService plans;
    private final OnboardingPlanRepository planRepo;
    private final PreboardingChecklistRepository checklists;
    private final ApplicationEventPublisher events;

    public PreboardingJoinedListener(
            OnboardingPlanService plans,
            OnboardingPlanRepository planRepo,
            PreboardingChecklistRepository checklists,
            ApplicationEventPublisher events) {
        this.plans = plans;
        this.planRepo = planRepo;
        this.checklists = checklists;
        this.events = events;
    }

    @EventListener
    @Transactional
    public void onOfferEvent(OfferEvent event) {
        if (event.eventType() != OfferEventType.PREBOARDING_CHECKLIST_JOINED) {
            return;
        }
        UUID checklistId = event.checklistId();
        if (checklistId == null) {
            LOG.warn(
                    "PREBOARDING_CHECKLIST_JOINED event tenant={} arrived without checklistId",
                    event.tenantId());
            return;
        }
        PreboardingChecklist checklist =
                checklists.findByIdAndTenantId(checklistId, event.tenantId()).orElse(null);
        if (checklist == null || checklist.getEmployee() == null) {
            LOG.info(
                    "Skipping onboarding-plan handoff for checklist={} — missing employee link",
                    checklistId);
            return;
        }

        // Idempotency 1: don't spawn if a plan already exists for the checklist.
        if (planRepo.findByTenantIdAndSourceChecklistId(event.tenantId(), checklistId)
                .isPresent()) {
            LOG.debug("Onboarding plan already exists for checklist={}", checklistId);
            return;
        }
        // Idempotency 2: don't spawn if the employee already has any plan.
        if (planRepo.findByTenantIdAndEmployeeId(event.tenantId(), checklist.getEmployee().getId())
                .isPresent()) {
            LOG.debug(
                    "Onboarding plan already exists for employee={}",
                    checklist.getEmployee().getId());
            return;
        }

        CreateOnboardingPlanRequest req =
                new CreateOnboardingPlanRequest(
                        event.tenantId(),
                        event.companyId(),
                        checklist.getEmployee().getId(),
                        event.offerId(),
                        checklistId,
                        checklist.getJoiningDate(),
                        null,
                        null,
                        null);
        plans.createInternal(req);
        events.publishEvent(
                new OnboardingEvent(
                        OnboardingEventType.PLAN_CREATED,
                        event.tenantId(),
                        event.companyId(),
                        null,
                        checklist.getEmployee().getId(),
                        null,
                        null,
                        "handoff-from-preboarding",
                        null,
                        Instant.now()));
    }
}
