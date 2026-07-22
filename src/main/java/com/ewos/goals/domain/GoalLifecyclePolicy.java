package com.ewos.goals.domain;

import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Transition + integrity guards for goals. */
@Component
public class GoalLifecyclePolicy {

    private static final Set<GoalStatus> TERMINAL =
            EnumSet.of(GoalStatus.COMPLETED, GoalStatus.CANCELLED);

    public void assertOpenable(Goal g) {
        if (!g.getPeriodEnd().isAfter(g.getPeriodStart())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "period_end must be strictly after period_start");
        }
        BigDecimal w = g.getWeightage();
        if (w != null && (w.signum() < 0 || w.compareTo(new BigDecimal("100")) > 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "weightage must be within [0,100]");
        }
        if (g.getScope() == GoalScope.INDIVIDUAL && g.getEmployee() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Individual goals require an employee");
        }
        if ((g.getScope() == GoalScope.TEAM || g.getScope() == GoalScope.DEPARTMENT)
                && g.getOrgUnitId() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Team / department goals require an orgUnitId");
        }
    }

    public void assertAssignable(Goal g) {
        if (g.getStatus() != GoalStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only DRAFT goals can be assigned (status=" + g.getStatus() + ")");
        }
    }

    public void assertUpdatable(Goal g) {
        if (TERMINAL.contains(g.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot update a terminal goal (status=" + g.getStatus() + ")");
        }
    }

    public void assertProgressRecordable(Goal g) {
        if (g.getStatus() != GoalStatus.ASSIGNED && g.getStatus() != GoalStatus.IN_PROGRESS) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Progress can only be recorded on ASSIGNED / IN_PROGRESS goals (status="
                            + g.getStatus()
                            + ")");
        }
    }

    public void assertProgressValueValid(BigDecimal progressPercent) {
        if (progressPercent == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "progressPercent is required");
        }
        if (progressPercent.signum() < 0 || progressPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "progressPercent must be within [0,100]");
        }
    }

    public void assertReviewable(Goal g) {
        if (g.getStatus() != GoalStatus.IN_PROGRESS && g.getStatus() != GoalStatus.UNDER_REVIEW) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only IN_PROGRESS / UNDER_REVIEW goals can be reviewed (status="
                            + g.getStatus()
                            + ")");
        }
    }

    public void assertClosable(Goal g) {
        if (TERMINAL.contains(g.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Goal is already terminal (status=" + g.getStatus() + ")");
        }
    }

    public boolean isTerminal(GoalStatus status) {
        return TERMINAL.contains(status);
    }
}
