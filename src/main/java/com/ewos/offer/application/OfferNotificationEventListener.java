package com.ewos.offer.application;

import com.ewos.employee.domain.Employee;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.offer.domain.Offer;
import com.ewos.offer.domain.events.OfferEvent;
import com.ewos.offer.infrastructure.persistence.OfferRepository;
import com.ewos.recruitment.domain.JobRequisition;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24F — bridges {@link OfferEvent}s to the in-app notification inbox, mirroring {@code
 * PerformanceNotificationEventListener}'s shape. {@code OfferEvent}s themselves already existed and
 * were already published by {@link OfferService}/preboarding services — nothing was listening to
 * them before this class. Like {@code InterviewNotificationEventListener}, this is the internal
 * half of what {@code NoOpOfferNotifier} deliberately leaves as a no-op default (candidate-facing
 * offer letters/reminders need an external channel this in-app inbox can't provide); this listener
 * covers the one internal recipient every offer has — {@code jobRequisition.hiringManager}.
 *
 * <p>{@code OFFER_TEMPLATE_*}, {@code OFFER_DRAFTED/SUBMITTED/REVISED/NEGOTIATION_LOGGED}, and
 * {@code OFFER_APPROVED/REJECTED/WITHDRAWN} are recruiter/approver-authored bookkeeping — approval
 * itself already notifies the approver via the Workflow Engine's {@code TASK_ASSIGNED}. {@code
 * PREBOARDING_CHECKLIST_JOINED} is already handled by {@code
 * com.ewos.onboarding.application.PreboardingJoinedListener}, which opens the onboarding plan;
 * duplicating a notification here would be a second, redundant listener reacting to the same event.
 * Other {@code PREBOARDING_*} events have no single unambiguous individual recipient beyond the
 * checklist owner, who is the hiring manager only by convention, not by a modeled field.
 */
@Component
public class OfferNotificationEventListener {

    private final NotificationService notifications;
    private final OfferRepository offers;

    public OfferNotificationEventListener(
            NotificationService notifications, OfferRepository offers) {
        this.notifications = notifications;
        this.offers = offers;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOfferEvent(OfferEvent event) {
        switch (event.eventType()) {
            case OFFER_ACCEPTED ->
                    notifyHiringManager(
                            event,
                            NotificationType.OFFER_ACCEPTED,
                            "Offer accepted",
                            "The candidate accepted offer {{offerNumber}}");
            case OFFER_DECLINED ->
                    notifyHiringManager(
                            event,
                            NotificationType.OFFER_DECLINED,
                            "Offer declined",
                            "The candidate declined offer {{offerNumber}}");
            case OFFER_EXPIRED ->
                    notifyHiringManager(
                            event,
                            NotificationType.OFFER_EXPIRED,
                            "Offer expired",
                            "Offer {{offerNumber}} expired without a response");
            default -> {
                // See class javadoc for why the remaining event types are not notification-worthy.
            }
        }
    }

    private void notifyHiringManager(
            OfferEvent event, NotificationType type, String title, String body) {
        if (event.offerId() == null) {
            return;
        }
        offers.findByIdAndTenantId(event.offerId(), event.tenantId())
                .map(Offer::getJobRequisition)
                .map(JobRequisition::getHiringManager)
                .map(Employee::getUserId)
                .ifPresent(
                        userId ->
                                notifications.send(
                                        event.tenantId(),
                                        userId,
                                        type,
                                        title,
                                        body,
                                        null,
                                        Map.of(
                                                "offerNumber",
                                                event.offerNumber() == null
                                                        ? "the offer"
                                                        : event.offerNumber())));
    }
}
