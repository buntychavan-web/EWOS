package com.ewos.performance.application;

import com.ewos.performance.domain.Appraisal;
import com.ewos.performance.domain.events.PerformanceEvent;
import com.ewos.performance.domain.events.PerformanceEventType;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24B — scheduled sweep for overdue self/manager/reviewer assessments, mirroring {@code
 * PurgeJob}'s shape: off by default (an operator must opt in via {@code
 * app.performance.reminders.enabled}), one bounded query per stage per run ({@code
 * app.performance.reminders.batch-size} rows, oldest-overdue-first) so a large backlog is worked
 * off over several runs rather than flooding the notification inbox in one sweep. Each row found
 * publishes exactly one {@link PerformanceEventType#SELF_REVIEW_REMINDER} (or the manager/reviewer
 * equivalent), which {@link PerformanceNotificationEventListener} turns into an in-app row.
 */
@Component
public class PerformanceReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PerformanceReminderJob.class);

    private final AppraisalRepository appraisals;
    private final ApplicationEventPublisher events;

    @Value("${app.performance.reminders.enabled:false}")
    private boolean enabled;

    @Value("${app.performance.reminders.batch-size:5000}")
    private int batchSize;

    public PerformanceReminderJob(
            AppraisalRepository appraisals, ApplicationEventPublisher events) {
        this.appraisals = appraisals;
        this.events = events;
    }

    @Scheduled(
            cron = "${app.performance.reminders.cron:0 30 6 * * *}",
            zone = "${app.performance.reminders.zone:UTC}")
    @Transactional(readOnly = true)
    public void runAll() {
        if (!enabled) {
            return;
        }
        Pageable page = PageRequest.of(0, batchSize);
        LocalDate today = LocalDate.now();
        int self =
                remind(
                        appraisals.findOverdueSelfReviews(today, page),
                        PerformanceEventType.SELF_REVIEW_REMINDER);
        int manager =
                remind(
                        appraisals.findOverdueManagerReviews(today, page),
                        PerformanceEventType.MANAGER_REVIEW_REMINDER);
        int reviewer =
                remind(
                        appraisals.findOverdueReviewerReviews(today, page),
                        PerformanceEventType.REVIEWER_REVIEW_REMINDER);
        log.info(
                "Performance reminder sweep: {} self, {} manager, {} reviewer reminder(s) sent",
                self,
                manager,
                reviewer);
    }

    private int remind(List<Appraisal> overdue, PerformanceEventType type) {
        for (Appraisal a : overdue) {
            events.publishEvent(
                    new PerformanceEvent(
                            type,
                            a.getTenantId(),
                            a.getCompanyId(),
                            a.getCycle().getId(),
                            null,
                            a.getId(),
                            a.getEmployee() == null ? null : a.getEmployee().getId(),
                            null,
                            null,
                            null,
                            null,
                            Instant.now()));
        }
        return overdue.size();
    }

    /** Public trigger for tests / operator scripts. Bypasses the schedule but respects the flag. */
    public void runNow() {
        runAll();
    }
}
