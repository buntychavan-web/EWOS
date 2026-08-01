package com.ewos.goals.application;

import com.ewos.goals.domain.Goal;
import com.ewos.goals.domain.events.GoalEvent;
import com.ewos.goals.domain.events.GoalEventType;
import com.ewos.goals.infrastructure.persistence.GoalRepository;
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
 * Sprint 24E — scheduled sweep for goals approaching or past their {@code period_end}, mirroring
 * {@code PerformanceReminderJob}'s shape exactly: off by default ({@code
 * app.goals.reminders.enabled}), one bounded query per stage per run ({@code
 * app.goals.reminders.batch-size} rows, oldest-due-first). Each row found publishes exactly one
 * {@link GoalEventType#GOAL_DUE_REMINDER} or {@link GoalEventType#GOAL_OVERDUE}, which {@link
 * GoalNotificationEventListener} turns into an in-app row.
 */
@Component
public class GoalReminderJob {

    private static final Logger log = LoggerFactory.getLogger(GoalReminderJob.class);

    private final GoalRepository goals;
    private final ApplicationEventPublisher events;

    @Value("${app.goals.reminders.enabled:false}")
    private boolean enabled;

    @Value("${app.goals.reminders.batch-size:5000}")
    private int batchSize;

    @Value("${app.goals.reminders.due-soon-days:3}")
    private int dueSoonDays;

    public GoalReminderJob(GoalRepository goals, ApplicationEventPublisher events) {
        this.goals = goals;
        this.events = events;
    }

    @Scheduled(
            cron = "${app.goals.reminders.cron:0 45 6 * * *}",
            zone = "${app.goals.reminders.zone:UTC}")
    @Transactional(readOnly = true)
    public void runAll() {
        if (!enabled) {
            return;
        }
        Pageable page = PageRequest.of(0, batchSize);
        LocalDate today = LocalDate.now();
        int dueSoon =
                remind(
                        goals.findDueSoon(today, today.plusDays(dueSoonDays), page),
                        GoalEventType.GOAL_DUE_REMINDER);
        int overdue = remind(goals.findOverdue(today, page), GoalEventType.GOAL_OVERDUE);
        log.info("Goal reminder sweep: {} due-soon, {} overdue reminder(s) sent", dueSoon, overdue);
    }

    private int remind(List<Goal> found, GoalEventType type) {
        for (Goal g : found) {
            events.publishEvent(
                    new GoalEvent(
                            type,
                            g.getTenantId(),
                            g.getCompanyId(),
                            g.getId(),
                            null,
                            g.getEmployee() == null ? null : g.getEmployee().getId(),
                            g.getOrgUnitId(),
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
