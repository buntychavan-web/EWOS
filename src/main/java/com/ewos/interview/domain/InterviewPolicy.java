package com.ewos.interview.domain;

import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Lifecycle guards for an {@link InterviewRound}. */
@Component
public class InterviewPolicy {

    private static final Set<InterviewStatus> TERMINAL =
            EnumSet.of(
                    InterviewStatus.COMPLETED,
                    InterviewStatus.CANCELLED,
                    InterviewStatus.NO_SHOW,
                    InterviewStatus.PENDING_FEEDBACK);

    private static final Set<InterviewStatus> RESCHEDULABLE =
            EnumSet.of(InterviewStatus.SCHEDULED, InterviewStatus.RESCHEDULED);

    private static final Set<InterviewStatus> STARTABLE =
            EnumSet.of(InterviewStatus.SCHEDULED, InterviewStatus.RESCHEDULED);

    public void assertSchedulable(InterviewRound r, Instant start, Instant end) {
        if (r.getStatus() != InterviewStatus.DRAFT && r.getStatus() != InterviewStatus.CANCELLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Interview must be DRAFT or CANCELLED to schedule (current: "
                            + r.getStatus()
                            + ")");
        }
        assertSchedule(start, end);
    }

    public void assertReschedulable(InterviewRound r, Instant start, Instant end) {
        if (!RESCHEDULABLE.contains(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Interview must be SCHEDULED or RESCHEDULED to reschedule (current: "
                            + r.getStatus()
                            + ")");
        }
        assertSchedule(start, end);
    }

    public void assertStartable(InterviewRound r) {
        if (!STARTABLE.contains(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Interview must be SCHEDULED / RESCHEDULED to start (current: "
                            + r.getStatus()
                            + ")");
        }
    }

    public void assertCompletable(InterviewRound r) {
        if (r.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Interview must be IN_PROGRESS to complete (current: " + r.getStatus() + ")");
        }
    }

    public void assertCancellable(InterviewRound r) {
        if (r.getStatus() == InterviewStatus.COMPLETED
                || r.getStatus() == InterviewStatus.CANCELLED
                || r.getStatus() == InterviewStatus.PENDING_FEEDBACK) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Interview is already terminal (current: " + r.getStatus() + ")");
        }
    }

    public void assertNoShowable(InterviewRound r) {
        if (!RESCHEDULABLE.contains(r.getStatus())
                && r.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Interview must be SCHEDULED / RESCHEDULED / IN_PROGRESS to mark no-show"
                            + " (current: "
                            + r.getStatus()
                            + ")");
        }
    }

    public void assertDecidable(InterviewRound r) {
        if (r.getStatus() != InterviewStatus.COMPLETED
                && r.getStatus() != InterviewStatus.PENDING_FEEDBACK) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Interview must be COMPLETED or PENDING_FEEDBACK to record a decision"
                            + " (current: "
                            + r.getStatus()
                            + ")");
        }
    }

    public void assertScorecardSubmittable(InterviewRound r) {
        if (r.getStatus() == InterviewStatus.DRAFT || r.getStatus() == InterviewStatus.CANCELLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Scorecards cannot be submitted for DRAFT or CANCELLED rounds");
        }
    }

    public boolean isTerminal(InterviewStatus status) {
        return TERMINAL.contains(status);
    }

    private void assertSchedule(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Both start and end are required to schedule");
        }
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End must be strictly after start");
        }
    }
}
