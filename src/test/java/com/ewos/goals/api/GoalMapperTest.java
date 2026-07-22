package com.ewos.goals.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.employee.domain.Employee;
import com.ewos.goals.api.dto.GoalLibraryItemResponse;
import com.ewos.goals.api.dto.GoalReportRowResponse;
import com.ewos.goals.api.dto.GoalResponse;
import com.ewos.goals.domain.Goal;
import com.ewos.goals.domain.GoalLibraryItem;
import com.ewos.goals.domain.GoalPriority;
import com.ewos.goals.domain.GoalScope;
import com.ewos.goals.domain.GoalStatus;
import com.ewos.goals.domain.GoalType;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GoalMapperTest {

    private final GoalMapper mapper = new GoalMapper();

    @Test
    void libraryItemRoundTrip() {
        GoalLibraryItem g = new GoalLibraryItem();
        setId(g, UUID.randomUUID());
        g.setTenantId(UUID.randomUUID());
        g.setCompanyId(UUID.randomUUID());
        g.setCode("REV-Q");
        g.setName("Quarterly Revenue");
        g.setGoalType(GoalType.KPI);
        g.setCategory("Financial");
        g.setDefaultWeightage(new BigDecimal("30.00"));
        g.setDefaultTarget("1M");
        g.setUnitOfMeasure("USD");
        g.setActive(true);
        GoalLibraryItemResponse res = mapper.toResponse(g);
        assertThat(res.code()).isEqualTo("REV-Q");
        assertThat(res.goalType()).isEqualTo(GoalType.KPI);
        assertThat(res.defaultWeightage()).isEqualTo(new BigDecimal("30.00"));
        assertThat(res.active()).isTrue();
    }

    @Test
    void goalResponseIncludesAllFields() {
        Goal g = new Goal();
        setId(g, UUID.randomUUID());
        g.setTenantId(UUID.randomUUID());
        g.setCompanyId(UUID.randomUUID());
        g.setCode("G1");
        g.setName("Improve satisfaction");
        g.setGoalType(GoalType.OKR);
        g.setScope(GoalScope.INDIVIDUAL);
        g.setPeriodStart(LocalDate.of(2026, 4, 1));
        g.setPeriodEnd(LocalDate.of(2026, 9, 30));
        g.setWeightage(new BigDecimal("40.00"));
        g.setPriority(GoalPriority.HIGH);
        g.setStatus(GoalStatus.IN_PROGRESS);
        g.setProgressPercent(new BigDecimal("55.00"));
        GoalResponse res = mapper.toResponse(g);
        assertThat(res.code()).isEqualTo("G1");
        assertThat(res.status()).isEqualTo(GoalStatus.IN_PROGRESS);
        assertThat(res.priority()).isEqualTo(GoalPriority.HIGH);
        assertThat(res.progressPercent()).isEqualTo(new BigDecimal("55.00"));
    }

    @Test
    void reportRowIncludesEmployeeSummary() {
        Employee e = new Employee();
        setId(e, UUID.randomUUID());
        e.setEmployeeNumber("E-42");
        e.setFirstName("Grace");
        e.setLastName("Hopper");
        Goal g = new Goal();
        setId(g, UUID.randomUUID());
        g.setCode("G2");
        g.setName("Increase compilers");
        g.setGoalType(GoalType.KRA);
        g.setScope(GoalScope.INDIVIDUAL);
        g.setEmployee(e);
        g.setStatus(GoalStatus.COMPLETED);
        g.setWeightage(new BigDecimal("25"));
        g.setProgressPercent(new BigDecimal("100"));
        g.setReviewScore(new BigDecimal("4.5"));
        GoalReportRowResponse row = mapper.toReportRow(g);
        assertThat(row.employeeName()).isEqualTo("Grace Hopper");
        assertThat(row.employeeNumber()).isEqualTo("E-42");
        assertThat(row.reviewScore()).isEqualTo(new BigDecimal("4.5"));
    }

    @Test
    void reportRowFallsBackWithoutEmployee() {
        Goal g = new Goal();
        setId(g, UUID.randomUUID());
        g.setCode("G3");
        g.setName("Company-wide");
        g.setGoalType(GoalType.OKR);
        g.setScope(GoalScope.COMPANY);
        g.setStatus(GoalStatus.IN_PROGRESS);
        g.setProgressPercent(BigDecimal.ZERO);
        g.setWeightage(new BigDecimal("100"));
        GoalReportRowResponse row = mapper.toReportRow(g);
        assertThat(row.employeeId()).isNull();
        assertThat(row.employeeNumber()).isNull();
        assertThat(row.employeeName()).isNull();
    }

    private static void setId(Object entity, UUID id) {
        try {
            Class<?> c = entity.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(entity, id);
                    return;
                } catch (NoSuchFieldException ignore) {
                    c = c.getSuperclass();
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
