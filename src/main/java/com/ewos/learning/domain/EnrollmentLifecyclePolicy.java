package com.ewos.learning.domain;

import com.ewos.shared.exception.ApiException;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Transition + integrity guards for enrollments + sessions. */
@Component
public class EnrollmentLifecyclePolicy {

    private static final Set<EnrollmentStatus> TERMINAL =
            EnumSet.of(
                    EnrollmentStatus.COMPLETED,
                    EnrollmentStatus.WITHDRAWN,
                    EnrollmentStatus.NO_SHOW,
                    EnrollmentStatus.FAILED);

    public void assertNominatable(TrainingCourse course) {
        if (!course.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Course is not active");
        }
    }

    public void assertEnrollable(TrainingEnrollment e) {
        if (e.getStatus() != EnrollmentStatus.NOMINATED
                && e.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only NOMINATED / ENROLLED enrollments may be enrolled (status="
                            + e.getStatus()
                            + ")");
        }
    }

    public void assertStartable(TrainingEnrollment e) {
        if (e.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only ENROLLED enrollments may start (status=" + e.getStatus() + ")");
        }
    }

    public void assertAttendanceRecordable(TrainingEnrollment e) {
        if (TERMINAL.contains(e.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot record attendance on a terminal enrollment (status="
                            + e.getStatus()
                            + ")");
        }
    }

    public void assertCompletable(TrainingEnrollment e) {
        if (e.getStatus() != EnrollmentStatus.IN_PROGRESS
                && e.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only ENROLLED / IN_PROGRESS enrollments may be completed (status="
                            + e.getStatus()
                            + ")");
        }
    }

    public void assertWithdrawable(TrainingEnrollment e) {
        if (TERMINAL.contains(e.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Enrollment is already terminal (status=" + e.getStatus() + ")");
        }
    }

    public void assertSessionScheduleValid(TrainingSession s) {
        if (!s.getEndsAt().isAfter(s.getStartsAt())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "ends_at must be strictly after starts_at");
        }
    }

    public boolean isTerminal(EnrollmentStatus status) {
        return TERMINAL.contains(status);
    }
}
