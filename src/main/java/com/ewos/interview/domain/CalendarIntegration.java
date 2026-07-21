package com.ewos.interview.domain;

import java.util.List;
import java.util.UUID;

/**
 * Contract for a calendar integration used to reserve time on interviewers' calendars, generate a
 * conference link, and record cancellations. Deployments plug in a Google Workspace / Microsoft 365
 * / Zoom implementation as a {@code @Primary} bean; the default in-tree binding no-ops.
 */
public interface CalendarIntegration {

    /**
     * Reserve time for the given round. Returns an opaque external reference (e.g. a Google
     * Calendar event ID) to store on the round, or {@code null} if the integration cannot handle
     * this round.
     */
    String scheduleRound(InterviewRound round, List<UUID> participantEmployeeIds);

    /** Update an existing external event with a new start / end. */
    String rescheduleRound(InterviewRound round, List<UUID> participantEmployeeIds);

    /** Cancel the external event held for this round. */
    void cancelRound(InterviewRound round);

    /** Identifier of the calendar backend (recorded for audit). */
    String providerId();
}
