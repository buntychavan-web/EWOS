package com.ewos.probation.domain;

import com.ewos.shared.exception.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Transition + integrity guards for probation records. */
@Component
public class ProbationLifecyclePolicy {

    private static final Set<ProbationStatus> TERMINAL =
            EnumSet.of(
                    ProbationStatus.CONFIRMED,
                    ProbationStatus.TERMINATED,
                    ProbationStatus.CANCELLED);

    private final Clock clock;

    public ProbationLifecyclePolicy() {
        this(Clock.systemUTC());
    }

    ProbationLifecyclePolicy(Clock clock) {
        this.clock = clock;
    }

    public void assertOpenable(ProbationRecord r) {
        if (!r.getPeriodEnd().isAfter(r.getPeriodStart())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "period_end must be strictly after period_start");
        }
    }

    public void assertExtendable(ProbationRecord r, LocalDate newEnd) {
        if (r.getStatus() != ProbationStatus.IN_PROBATION
                && r.getStatus() != ProbationStatus.EXTENDED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Cannot extend from status " + r.getStatus());
        }
        LocalDate current = r.effectiveEnd();
        if (!newEnd.isAfter(current)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "New end date must be after current effective end");
        }
        if (r.getPolicy() != null && r.getPolicy().getMaxExtensionDays() > 0) {
            long extra = java.time.temporal.ChronoUnit.DAYS.between(r.getPeriodEnd(), newEnd);
            if (extra > r.getPolicy().getMaxExtensionDays()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Extension exceeds policy max_extension_days ("
                                + r.getPolicy().getMaxExtensionDays()
                                + ")");
            }
        }
    }

    public void assertReviewable(ProbationRecord r) {
        if (TERMINAL.contains(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Record is already terminal (" + r.getStatus() + ")");
        }
    }

    public void assertRecommendable(ProbationRecord r) {
        if (TERMINAL.contains(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Record is already terminal (" + r.getStatus() + ")");
        }
    }

    public void assertConfirmable(ProbationRecord r) {
        if (TERMINAL.contains(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Record is already terminal (" + r.getStatus() + ")");
        }
        boolean early = LocalDate.now(clock).isBefore(r.effectiveEnd());
        if (early && (r.getPolicy() == null || !r.getPolicy().isAllowEarlyConfirm())) {
            throw new ApiException(HttpStatus.CONFLICT, "Policy does not allow early confirmation");
        }
    }

    public void assertTerminable(ProbationRecord r) {
        if (TERMINAL.contains(r.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Record is already terminal (" + r.getStatus() + ")");
        }
    }

    public boolean isTerminal(ProbationStatus status) {
        return TERMINAL.contains(status);
    }
}
