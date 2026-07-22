package com.ewos.goals.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.employee.domain.Employee;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GoalLifecyclePolicyTest {

    private final GoalLifecyclePolicy policy = new GoalLifecyclePolicy();

    @Test
    void assertOpenable_rejectsInvertedPeriod() {
        Goal g = newIndividualGoal();
        g.setPeriodEnd(g.getPeriodStart());
        assertThatThrownBy(() -> policy.assertOpenable(g))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("strictly after");
    }

    @Test
    void assertOpenable_rejectsInvalidWeightage() {
        Goal g = newIndividualGoal();
        g.setWeightage(new BigDecimal("150"));
        assertThatThrownBy(() -> policy.assertOpenable(g))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("weightage must be within");
    }

    @Test
    void assertOpenable_rejectsIndividualWithoutEmployee() {
        Goal g = newIndividualGoal();
        g.setEmployee(null);
        assertThatThrownBy(() -> policy.assertOpenable(g))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("require an employee");
    }

    @Test
    void assertOpenable_rejectsTeamWithoutOrgUnit() {
        Goal g = newIndividualGoal();
        g.setScope(GoalScope.TEAM);
        g.setEmployee(null);
        assertThatThrownBy(() -> policy.assertOpenable(g))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("orgUnitId");
    }

    @Test
    void assertAssignable_rejectsNonDraft() {
        Goal g = newIndividualGoal();
        g.setStatus(GoalStatus.ASSIGNED);
        assertThatThrownBy(() -> policy.assertAssignable(g)).isInstanceOf(ApiException.class);
    }

    @Test
    void assertUpdatable_rejectsTerminal() {
        Goal g = newIndividualGoal();
        g.setStatus(GoalStatus.COMPLETED);
        assertThatThrownBy(() -> policy.assertUpdatable(g)).isInstanceOf(ApiException.class);
    }

    @Test
    void assertProgressRecordable_rejectsWrongStatus() {
        Goal g = newIndividualGoal();
        g.setStatus(GoalStatus.DRAFT);
        assertThatThrownBy(() -> policy.assertProgressRecordable(g))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void assertProgressValueValid_rejectsOutOfRange() {
        assertThatThrownBy(() -> policy.assertProgressValueValid(new BigDecimal("101")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.assertProgressValueValid(new BigDecimal("-1")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void assertProgressValueValid_acceptsBoundary() {
        assertThatCode(() -> policy.assertProgressValueValid(BigDecimal.ZERO))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertProgressValueValid(new BigDecimal("100")))
                .doesNotThrowAnyException();
    }

    @Test
    void assertReviewable_rejectsDraft() {
        Goal g = newIndividualGoal();
        g.setStatus(GoalStatus.DRAFT);
        assertThatThrownBy(() -> policy.assertReviewable(g)).isInstanceOf(ApiException.class);
    }

    @Test
    void isTerminal_recognisesCompletedAndCancelled() {
        assertThat(policy.isTerminal(GoalStatus.COMPLETED)).isTrue();
        assertThat(policy.isTerminal(GoalStatus.CANCELLED)).isTrue();
        assertThat(policy.isTerminal(GoalStatus.DRAFT)).isFalse();
    }

    private Goal newIndividualGoal() {
        Goal g = new Goal();
        Employee e = new Employee();
        g.setEmployee(e);
        g.setScope(GoalScope.INDIVIDUAL);
        g.setPeriodStart(LocalDate.of(2026, 4, 1));
        g.setPeriodEnd(LocalDate.of(2026, 9, 30));
        g.setWeightage(new BigDecimal("25.00"));
        g.setStatus(GoalStatus.DRAFT);
        g.setEmployee(new Employee());
        try {
            java.lang.reflect.Field idField = Employee.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(g.getEmployee(), UUID.randomUUID());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return g;
    }
}
