package com.ewos.performance.domain;

import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Transition + integrity guards for appraisal records. */
@Component
public class AppraisalLifecyclePolicy {

    private static final Set<AppraisalStatus> TERMINAL =
            EnumSet.of(AppraisalStatus.FINALISED, AppraisalStatus.CANCELLED);

    public void assertOpenable(PerformanceCycle cycle, AppraisalTemplate template) {
        if (cycle.getStatus() == PerformanceCycleStatus.CLOSED
                || cycle.getStatus() == PerformanceCycleStatus.CANCELLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot open appraisal in cycle status " + cycle.getStatus());
        }
        if (!template.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Template is inactive");
        }
    }

    public void assertSelfSubmittable(Appraisal a) {
        if (a.getStatus() != AppraisalStatus.PENDING_SELF) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Self assessment already submitted or not required (status="
                            + a.getStatus()
                            + ")");
        }
    }

    public void assertManagerSubmittable(Appraisal a) {
        if (a.getStatus() != AppraisalStatus.PENDING_MANAGER) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Manager assessment cannot be submitted from status " + a.getStatus());
        }
    }

    public void assertReviewerSubmittable(Appraisal a) {
        if (a.getStatus() != AppraisalStatus.PENDING_REVIEWER) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Reviewer assessment cannot be submitted from status " + a.getStatus());
        }
    }

    public void assertCalibratable(Appraisal a) {
        if (a.getStatus() != AppraisalStatus.CALIBRATION) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Appraisal is not in CALIBRATION (status=" + a.getStatus() + ")");
        }
    }

    public void assertSubmittableForApproval(Appraisal a) {
        if (a.getStatus() != AppraisalStatus.CALIBRATION) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only calibrated appraisals may be submitted for approval (status="
                            + a.getStatus()
                            + ")");
        }
        if (a.getCalibratedRating() == null && a.getReviewerRating() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Appraisal must have a reviewer or calibrated rating before approval");
        }
    }

    public void assertFinalisable(Appraisal a) {
        if (a.getStatus() != AppraisalStatus.PENDING_APPROVAL) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Appraisal must be in PENDING_APPROVAL to finalise (status="
                            + a.getStatus()
                            + ")");
        }
    }

    public void assertNotTerminal(Appraisal a) {
        if (TERMINAL.contains(a.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Appraisal is already terminal (status=" + a.getStatus() + ")");
        }
    }

    public boolean isTerminal(AppraisalStatus status) {
        return TERMINAL.contains(status);
    }

    public void assertRatingInScale(BigDecimal rating, AppraisalTemplate template) {
        if (rating == null) {
            return;
        }
        BigDecimal min = new BigDecimal(template.getRatingScaleMin());
        BigDecimal max = new BigDecimal(template.getRatingScaleMax());
        if (rating.compareTo(min) < 0 || rating.compareTo(max) > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Rating "
                            + rating
                            + " is outside scale ["
                            + template.getRatingScaleMin()
                            + ", "
                            + template.getRatingScaleMax()
                            + "]");
        }
    }
}
