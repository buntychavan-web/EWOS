package com.ewos.performance.domain;

import com.ewos.shared.exception.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Sprint 24B — strict transition graph for {@link PerformanceCycleStatus}, mirroring {@link
 * AppraisalLifecyclePolicy}'s role for {@link AppraisalStatus}. Before this, {@code
 * PerformanceCycleService.transition()} only rejected transitions <em>from</em> a terminal status;
 * any other status pair was accepted, so a cycle could jump straight from {@code DRAFT} to {@code
 * CLOSED}. Every legal edge below is one step forward in the review chain, or a move to {@code
 * CANCELLED} from anywhere non-terminal — reopening a {@code CLOSED} or {@code CANCELLED} cycle is
 * never legal.
 */
@Component
public class PerformanceCycleLifecyclePolicy {

    private static final Set<PerformanceCycleStatus> TERMINAL =
            EnumSet.of(PerformanceCycleStatus.CLOSED, PerformanceCycleStatus.CANCELLED);

    private static final Map<PerformanceCycleStatus, Set<PerformanceCycleStatus>> TRANSITIONS =
            new EnumMap<>(PerformanceCycleStatus.class);

    static {
        TRANSITIONS.put(PerformanceCycleStatus.DRAFT, EnumSet.of(PerformanceCycleStatus.OPEN));
        TRANSITIONS.put(
                PerformanceCycleStatus.OPEN, EnumSet.of(PerformanceCycleStatus.SELF_REVIEW));
        TRANSITIONS.put(
                PerformanceCycleStatus.SELF_REVIEW,
                EnumSet.of(PerformanceCycleStatus.MANAGER_REVIEW));
        TRANSITIONS.put(
                PerformanceCycleStatus.MANAGER_REVIEW,
                EnumSet.of(PerformanceCycleStatus.REVIEWER_REVIEW));
        TRANSITIONS.put(
                PerformanceCycleStatus.REVIEWER_REVIEW,
                EnumSet.of(PerformanceCycleStatus.CALIBRATION));
        TRANSITIONS.put(
                PerformanceCycleStatus.CALIBRATION, EnumSet.of(PerformanceCycleStatus.HR_REVIEW));
        TRANSITIONS.put(
                PerformanceCycleStatus.HR_REVIEW,
                EnumSet.of(PerformanceCycleStatus.FINAL_APPROVAL));
        TRANSITIONS.put(
                PerformanceCycleStatus.FINAL_APPROVAL, EnumSet.of(PerformanceCycleStatus.RELEASED));
        TRANSITIONS.put(PerformanceCycleStatus.RELEASED, EnumSet.of(PerformanceCycleStatus.CLOSED));
        TRANSITIONS.put(
                PerformanceCycleStatus.CLOSED, EnumSet.noneOf(PerformanceCycleStatus.class));
        TRANSITIONS.put(
                PerformanceCycleStatus.CANCELLED, EnumSet.noneOf(PerformanceCycleStatus.class));
        // CANCELLED is reachable from every non-terminal status; added below rather than repeated
        // above so the forward chain reads cleanly.
        for (PerformanceCycleStatus status : PerformanceCycleStatus.values()) {
            if (!TERMINAL.contains(status)) {
                TRANSITIONS.get(status).add(PerformanceCycleStatus.CANCELLED);
            }
        }
    }

    public void assertValidTransition(PerformanceCycleStatus from, PerformanceCycleStatus to) {
        if (from == to) {
            throw new ApiException(HttpStatus.CONFLICT, "Cycle is already in status " + from);
        }
        if (TERMINAL.contains(from)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Cycle is already terminal (status=" + from + ")");
        }
        Set<PerformanceCycleStatus> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Illegal cycle transition " + from + " -> " + to);
        }
    }

    public boolean isTerminal(PerformanceCycleStatus status) {
        return TERMINAL.contains(status);
    }
}
