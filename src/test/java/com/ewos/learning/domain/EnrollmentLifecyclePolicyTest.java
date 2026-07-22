package com.ewos.learning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EnrollmentLifecyclePolicyTest {

    private final EnrollmentLifecyclePolicy policy = new EnrollmentLifecyclePolicy();

    @Test
    void assertNominatable_rejectsInactiveCourse() {
        TrainingCourse c = new TrainingCourse();
        c.setActive(false);
        assertThatThrownBy(() -> policy.assertNominatable(c))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void assertNominatable_acceptsActiveCourse() {
        TrainingCourse c = new TrainingCourse();
        c.setActive(true);
        assertThatCode(() -> policy.assertNominatable(c)).doesNotThrowAnyException();
    }

    @Test
    void assertEnrollable_rejectsCompleted() {
        TrainingEnrollment e = new TrainingEnrollment();
        e.setStatus(EnrollmentStatus.COMPLETED);
        assertThatThrownBy(() -> policy.assertEnrollable(e)).isInstanceOf(ApiException.class);
    }

    @Test
    void assertStartable_rejectsInProgress() {
        TrainingEnrollment e = new TrainingEnrollment();
        e.setStatus(EnrollmentStatus.IN_PROGRESS);
        assertThatThrownBy(() -> policy.assertStartable(e)).isInstanceOf(ApiException.class);
    }

    @Test
    void assertAttendanceRecordable_rejectsWithdrawn() {
        TrainingEnrollment e = new TrainingEnrollment();
        e.setStatus(EnrollmentStatus.WITHDRAWN);
        assertThatThrownBy(() -> policy.assertAttendanceRecordable(e))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void assertCompletable_rejectsNominated() {
        TrainingEnrollment e = new TrainingEnrollment();
        e.setStatus(EnrollmentStatus.NOMINATED);
        assertThatThrownBy(() -> policy.assertCompletable(e)).isInstanceOf(ApiException.class);
    }

    @Test
    void assertWithdrawable_rejectsAlreadyWithdrawn() {
        TrainingEnrollment e = new TrainingEnrollment();
        e.setStatus(EnrollmentStatus.WITHDRAWN);
        assertThatThrownBy(() -> policy.assertWithdrawable(e)).isInstanceOf(ApiException.class);
    }

    @Test
    void assertSessionScheduleValid_rejectsInvertedTimes() {
        TrainingSession s = new TrainingSession();
        s.setStartsAt(Instant.parse("2026-06-01T10:00:00Z"));
        s.setEndsAt(Instant.parse("2026-06-01T09:00:00Z"));
        assertThatThrownBy(() -> policy.assertSessionScheduleValid(s))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("strictly after");
    }

    @Test
    void isTerminal_matchesExpected() {
        assertThat(policy.isTerminal(EnrollmentStatus.COMPLETED)).isTrue();
        assertThat(policy.isTerminal(EnrollmentStatus.WITHDRAWN)).isTrue();
        assertThat(policy.isTerminal(EnrollmentStatus.NO_SHOW)).isTrue();
        assertThat(policy.isTerminal(EnrollmentStatus.FAILED)).isTrue();
        assertThat(policy.isTerminal(EnrollmentStatus.NOMINATED)).isFalse();
        assertThat(policy.isTerminal(EnrollmentStatus.IN_PROGRESS)).isFalse();
    }
}
