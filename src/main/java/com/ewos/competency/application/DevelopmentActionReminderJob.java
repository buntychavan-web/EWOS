package com.ewos.competency.application;

import com.ewos.competency.domain.DevelopmentAction;
import com.ewos.competency.domain.events.CompetencyEvent;
import com.ewos.competency.domain.events.CompetencyEventType;
import com.ewos.competency.infrastructure.persistence.DevelopmentActionRepository;
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
 * Sprint 24E — scheduled sweep for development-plan actions approaching or past their {@code
 * due_on}, mirroring {@code GoalReminderJob}/{@code PerformanceReminderJob}'s shape exactly: off by
 * default ({@code app.competency.reminders.enabled}), one bounded query per stage per run. Each
 * action found publishes exactly one {@link CompetencyEventType#ACTION_DUE_REMINDER} or {@link
 * CompetencyEventType#ACTION_OVERDUE}, which {@link CompetencyNotificationEventListener} turns into
 * an in-app row.
 */
@Component
public class DevelopmentActionReminderJob {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentActionReminderJob.class);

    private final DevelopmentActionRepository actions;
    private final ApplicationEventPublisher events;

    @Value("${app.competency.reminders.enabled:false}")
    private boolean enabled;

    @Value("${app.competency.reminders.batch-size:5000}")
    private int batchSize;

    @Value("${app.competency.reminders.due-soon-days:3}")
    private int dueSoonDays;

    public DevelopmentActionReminderJob(
            DevelopmentActionRepository actions, ApplicationEventPublisher events) {
        this.actions = actions;
        this.events = events;
    }

    @Scheduled(
            cron = "${app.competency.reminders.cron:0 0 7 * * *}",
            zone = "${app.competency.reminders.zone:UTC}")
    @Transactional(readOnly = true)
    public void runAll() {
        if (!enabled) {
            return;
        }
        Pageable page = PageRequest.of(0, batchSize);
        LocalDate today = LocalDate.now();
        int dueSoon =
                remind(
                        actions.findDueSoon(today, today.plusDays(dueSoonDays), page),
                        CompetencyEventType.ACTION_DUE_REMINDER);
        int overdue = remind(actions.findOverdue(today, page), CompetencyEventType.ACTION_OVERDUE);
        log.info(
                "Development action reminder sweep: {} due-soon, {} overdue reminder(s) sent",
                dueSoon,
                overdue);
    }

    private int remind(List<DevelopmentAction> found, CompetencyEventType type) {
        for (DevelopmentAction a : found) {
            if (a.getPlan() == null || a.getPlan().getEmployee() == null) {
                continue;
            }
            events.publishEvent(
                    new CompetencyEvent(
                            type,
                            a.getTenantId(),
                            a.getPlan().getCompanyId(),
                            a.getCompetency() == null ? null : a.getCompetency().getId(),
                            a.getPlan().getEmployee().getId(),
                            a.getPlan().getId(),
                            null,
                            null,
                            null,
                            Instant.now()));
        }
        return found.size();
    }

    /** Public trigger for tests / operator scripts. Bypasses the schedule but respects the flag. */
    public void runNow() {
        runAll();
    }
}
