package com.ewos.onboarding.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.employee.domain.Employee;
import com.ewos.onboarding.api.dto.OnboardingPlanResponse;
import com.ewos.onboarding.api.dto.OnboardingSurveyResponse;
import com.ewos.onboarding.api.dto.OnboardingTaskInstanceResponse;
import com.ewos.onboarding.api.dto.OnboardingTaskTemplateResponse;
import com.ewos.onboarding.domain.OnboardingPlan;
import com.ewos.onboarding.domain.OnboardingPlanStatus;
import com.ewos.onboarding.domain.OnboardingSurvey;
import com.ewos.onboarding.domain.OnboardingSurveyType;
import com.ewos.onboarding.domain.OnboardingTaskInstance;
import com.ewos.onboarding.domain.OnboardingTaskOwner;
import com.ewos.onboarding.domain.OnboardingTaskStatus;
import com.ewos.onboarding.domain.OnboardingTaskTemplate;
import com.ewos.onboarding.domain.OnboardingTaskType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OnboardingMapperTest {

    private final OnboardingMapper mapper = new OnboardingMapper();

    @Test
    void mapsTaskTemplate() {
        OnboardingTaskTemplate t = new OnboardingTaskTemplate();
        t.setTenantId(UUID.randomUUID());
        t.setCompanyId(UUID.randomUUID());
        t.setCode("ORIENT");
        t.setName("Orientation");
        t.setTaskType(OnboardingTaskType.ORIENTATION);
        t.setDefaultOwner(OnboardingTaskOwner.HR);
        t.setActive(true);

        OnboardingTaskTemplateResponse resp = mapper.toResponse(t);
        assertThat(resp.code()).isEqualTo("ORIENT");
        assertThat(resp.taskType()).isEqualTo(OnboardingTaskType.ORIENTATION);
        assertThat(resp.defaultOwner()).isEqualTo(OnboardingTaskOwner.HR);
    }

    @Test
    void mapsPlan() {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        OnboardingPlan p = new OnboardingPlan();
        p.setTenantId(UUID.randomUUID());
        p.setCompanyId(UUID.randomUUID());
        p.setEmployee(e);
        p.setStatus(OnboardingPlanStatus.IN_PROGRESS);
        p.setCompletionPercent(new BigDecimal("25.00"));

        OnboardingPlanResponse resp = mapper.toResponse(p);
        assertThat(resp.employeeId()).isEqualTo(e.getId());
        assertThat(resp.status()).isEqualTo(OnboardingPlanStatus.IN_PROGRESS);
        assertThat(resp.completionPercent()).isEqualByComparingTo("25.00");
    }

    @Test
    void mapsTaskInstance() {
        OnboardingPlan p = new OnboardingPlan();
        p.setId(UUID.randomUUID());
        OnboardingTaskInstance t = new OnboardingTaskInstance();
        t.setTenantId(UUID.randomUUID());
        t.setPlan(p);
        t.setName("Sign handbook");
        t.setTaskType(OnboardingTaskType.HANDBOOK);
        t.setOwner(OnboardingTaskOwner.EMPLOYEE);
        t.setStatus(OnboardingTaskStatus.IN_PROGRESS);

        OnboardingTaskInstanceResponse resp = mapper.toResponse(t);
        assertThat(resp.planId()).isEqualTo(p.getId());
        assertThat(resp.taskType()).isEqualTo(OnboardingTaskType.HANDBOOK);
        assertThat(resp.owner()).isEqualTo(OnboardingTaskOwner.EMPLOYEE);
        assertThat(resp.status()).isEqualTo(OnboardingTaskStatus.IN_PROGRESS);
    }

    @Test
    void mapsSurvey() {
        OnboardingPlan p = new OnboardingPlan();
        p.setId(UUID.randomUUID());
        OnboardingSurvey s = new OnboardingSurvey();
        s.setTenantId(UUID.randomUUID());
        s.setPlan(p);
        s.setSurveyType(OnboardingSurveyType.DAY_30);
        s.setOverallRating(new BigDecimal("8.50"));
        s.setComments("Going well");

        OnboardingSurveyResponse resp = mapper.toResponse(s);
        assertThat(resp.planId()).isEqualTo(p.getId());
        assertThat(resp.surveyType()).isEqualTo(OnboardingSurveyType.DAY_30);
        assertThat(resp.overallRating()).isEqualByComparingTo("8.50");
    }
}
