package com.ewos.performance.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.performance.domain.events.PerformanceEvent;
import com.ewos.performance.domain.events.PerformanceEventType;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository.AppraisalParticipantUserIds;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PerformanceNotificationEventListenerTest {

    @Mock NotificationService notifications;
    @Mock AppraisalRepository appraisals;

    private PerformanceNotificationEventListener listener;
    private UUID tenantId;
    private UUID appraisalId;
    private UUID employeeUserId;
    private UUID managerUserId;
    private UUID reviewerUserId;

    @BeforeEach
    void setUp() {
        listener = new PerformanceNotificationEventListener(notifications, appraisals);
        tenantId = UUID.randomUUID();
        appraisalId = UUID.randomUUID();
        employeeUserId = UUID.randomUUID();
        managerUserId = UUID.randomUUID();
        reviewerUserId = UUID.randomUUID();
    }

    private AppraisalParticipantUserIds participants() {
        return new AppraisalParticipantUserIds() {
            @Override
            public UUID getEmployeeUserId() {
                return employeeUserId;
            }

            @Override
            public UUID getManagerUserId() {
                return managerUserId;
            }

            @Override
            public UUID getReviewerUserId() {
                return reviewerUserId;
            }
        };
    }

    private PerformanceEvent event(PerformanceEventType type) {
        return new PerformanceEvent(
                type,
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                appraisalId,
                null,
                null,
                null,
                "detail",
                UUID.randomUUID(),
                Instant.now());
    }

    @Test
    void appraisalOpenedNotifiesTheEmployee() {
        when(appraisals.findParticipantUserIds(appraisalId, tenantId))
                .thenReturn(Optional.of(participants()));

        listener.onPerformanceEvent(event(PerformanceEventType.APPRAISAL_OPENED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.PERF_SELF_REVIEW_OPENED),
                        any(),
                        any(),
                        isNull());
    }

    @Test
    void selfAssessmentSubmittedNotifiesTheManager() {
        when(appraisals.findParticipantUserIds(appraisalId, tenantId))
                .thenReturn(Optional.of(participants()));

        listener.onPerformanceEvent(event(PerformanceEventType.SELF_ASSESSMENT_SUBMITTED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(managerUserId),
                        eq(NotificationType.PERF_MANAGER_REVIEW_PENDING),
                        any(),
                        any(),
                        isNull());
    }

    @Test
    void managerAssessmentSubmittedNotifiesTheReviewer() {
        when(appraisals.findParticipantUserIds(appraisalId, tenantId))
                .thenReturn(Optional.of(participants()));

        listener.onPerformanceEvent(event(PerformanceEventType.MANAGER_ASSESSMENT_SUBMITTED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(reviewerUserId),
                        eq(NotificationType.PERF_REVIEWER_REVIEW_PENDING),
                        any(),
                        any(),
                        isNull());
    }

    @Test
    void appraisalFinalisedNotifiesTheEmployee() {
        when(appraisals.findParticipantUserIds(appraisalId, tenantId))
                .thenReturn(Optional.of(participants()));

        listener.onPerformanceEvent(event(PerformanceEventType.APPRAISAL_FINALISED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.PERF_FINAL_RATING_RELEASED),
                        any(),
                        any(),
                        isNull());
    }

    @Test
    void cycleLaunchCompletedNotifiesTheRequestingActorDirectlyWithoutLookingUpAnAppraisal() {
        PerformanceEvent launchEvent =
                new PerformanceEvent(
                        PerformanceEventType.CYCLE_LAUNCH_COMPLETED,
                        tenantId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Matched 10, created 8",
                        managerUserId,
                        Instant.now());

        listener.onPerformanceEvent(launchEvent);

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(managerUserId),
                        eq(NotificationType.PERF_BULK_LAUNCH_COMPLETED),
                        any(),
                        eq("Matched 10, created 8"),
                        isNull());
        verify(appraisals, never()).findParticipantUserIds(any(), any());
    }

    @Test
    void unmappedEventTypesDoNothing() {
        listener.onPerformanceEvent(event(PerformanceEventType.CYCLE_CREATED));

        verify(notifications, never()).send(any(), any(), any(), any(), any(), any());
        verify(appraisals, never()).findParticipantUserIds(any(), any());
    }

    @Test
    void skipsLookupWhenEventHasNoAppraisalId() {
        PerformanceEvent noAppraisal =
                new PerformanceEvent(
                        PerformanceEventType.APPRAISAL_OPENED,
                        tenantId,
                        UUID.randomUUID(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.now());

        listener.onPerformanceEvent(noAppraisal);

        verify(appraisals, never()).findParticipantUserIds(any(), any());
        verify(notifications, never()).send(any(), any(), any(), any(), any(), any());
    }
}
