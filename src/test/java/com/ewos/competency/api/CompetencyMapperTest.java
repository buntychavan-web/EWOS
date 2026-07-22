package com.ewos.competency.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.competency.api.dto.AssessmentResponse;
import com.ewos.competency.api.dto.CompetencyResponse;
import com.ewos.competency.api.dto.EmployeeCompetencyResponse;
import com.ewos.competency.api.dto.PlanResponse;
import com.ewos.competency.api.dto.RoleCompetencyResponse;
import com.ewos.competency.domain.AssessmentType;
import com.ewos.competency.domain.Competency;
import com.ewos.competency.domain.CompetencyAssessment;
import com.ewos.competency.domain.DevelopmentPlan;
import com.ewos.competency.domain.DevelopmentPlanStatus;
import com.ewos.competency.domain.EmployeeCompetency;
import com.ewos.competency.domain.RoleCompetency;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompetencyMapperTest {

    private final CompetencyMapper mapper = new CompetencyMapper();

    @Test
    void competencyRoundTrip() {
        Competency c = new Competency();
        setId(c, UUID.randomUUID());
        c.setTenantId(UUID.randomUUID());
        c.setCompanyId(UUID.randomUUID());
        c.setCode("JAVA");
        c.setName("Java Programming");
        c.setCategory("Technical");
        c.setScaleMin(1);
        c.setScaleMax(5);
        c.setActive(true);
        CompetencyResponse res = mapper.toResponse(c);
        assertThat(res.code()).isEqualTo("JAVA");
        assertThat(res.scaleMax()).isEqualTo(5);
        assertThat(res.active()).isTrue();
    }

    @Test
    void roleCompetencyRoundTrip() {
        Competency c = new Competency();
        setId(c, UUID.randomUUID());
        RoleCompetency r = new RoleCompetency();
        setId(r, UUID.randomUUID());
        r.setCompetency(c);
        r.setRequiredLevel(3);
        r.setWeightage(new BigDecimal("40.00"));
        r.setDesignation("Senior Engineer");
        RoleCompetencyResponse res = mapper.toResponse(r);
        assertThat(res.requiredLevel()).isEqualTo(3);
        assertThat(res.weightage()).isEqualTo(new BigDecimal("40.00"));
        assertThat(res.designation()).isEqualTo("Senior Engineer");
    }

    @Test
    void employeeCompetencyRoundTrip() {
        Competency c = new Competency();
        setId(c, UUID.randomUUID());
        EmployeeCompetency e = new EmployeeCompetency();
        setId(e, UUID.randomUUID());
        e.setCompetency(c);
        e.setCurrentLevel(2);
        e.setTargetLevel(4);
        EmployeeCompetencyResponse res = mapper.toResponse(e);
        assertThat(res.currentLevel()).isEqualTo(2);
        assertThat(res.targetLevel()).isEqualTo(4);
    }

    @Test
    void assessmentRoundTrip() {
        Competency c = new Competency();
        setId(c, UUID.randomUUID());
        CompetencyAssessment a = new CompetencyAssessment();
        setId(a, UUID.randomUUID());
        a.setCompetency(c);
        a.setAssessmentType(AssessmentType.MANAGER);
        a.setAssessedLevel(4);
        AssessmentResponse res = mapper.toResponse(a);
        assertThat(res.assessmentType()).isEqualTo(AssessmentType.MANAGER);
        assertThat(res.assessedLevel()).isEqualTo(4);
    }

    @Test
    void planRoundTrip() {
        DevelopmentPlan p = new DevelopmentPlan();
        setId(p, UUID.randomUUID());
        p.setTitle("Q3 growth plan");
        p.setStatus(DevelopmentPlanStatus.ACTIVE);
        PlanResponse res = mapper.toResponse(p);
        assertThat(res.title()).isEqualTo("Q3 growth plan");
        assertThat(res.status()).isEqualTo(DevelopmentPlanStatus.ACTIVE);
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
