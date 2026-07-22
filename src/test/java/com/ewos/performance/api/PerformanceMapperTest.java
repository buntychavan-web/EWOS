package com.ewos.performance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.employee.domain.Employee;
import com.ewos.performance.api.dto.AppraisalReportRowResponse;
import com.ewos.performance.api.dto.AppraisalResponse;
import com.ewos.performance.api.dto.AppraisalTemplateResponse;
import com.ewos.performance.api.dto.PerformanceCycleResponse;
import com.ewos.performance.domain.Appraisal;
import com.ewos.performance.domain.AppraisalStatus;
import com.ewos.performance.domain.AppraisalTemplate;
import com.ewos.performance.domain.IncrementRecommendation;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.domain.PerformanceCycleStatus;
import com.ewos.performance.domain.PromotionRecommendation;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PerformanceMapperTest {

    private final PerformanceMapper mapper = new PerformanceMapper();

    @Test
    void cycleRoundTrip() {
        PerformanceCycle c = new PerformanceCycle();
        setId(c, UUID.randomUUID());
        c.setTenantId(UUID.randomUUID());
        c.setCompanyId(UUID.randomUUID());
        c.setCode("FY26");
        c.setName("FY26 review");
        c.setPeriodStart(LocalDate.of(2026, 4, 1));
        c.setPeriodEnd(LocalDate.of(2027, 3, 31));
        c.setStatus(PerformanceCycleStatus.OPEN);
        c.setBellCurveEnabled(true);
        PerformanceCycleResponse res = mapper.toResponse(c);
        assertThat(res.code()).isEqualTo("FY26");
        assertThat(res.status()).isEqualTo(PerformanceCycleStatus.OPEN);
        assertThat(res.bellCurveEnabled()).isTrue();
    }

    @Test
    void templateRoundTrip() {
        AppraisalTemplate t = new AppraisalTemplate();
        setId(t, UUID.randomUUID());
        t.setTenantId(UUID.randomUUID());
        t.setCompanyId(UUID.randomUUID());
        t.setCode("STD-5");
        t.setName("Standard 5-point");
        t.setRatingScaleMin(1);
        t.setRatingScaleMax(5);
        AppraisalTemplateResponse res = mapper.toResponse(t);
        assertThat(res.code()).isEqualTo("STD-5");
        assertThat(res.ratingScaleMax()).isEqualTo(5);
    }

    @Test
    void appraisalResponsePreservesRatings() {
        Appraisal a = new Appraisal();
        setId(a, UUID.randomUUID());
        a.setTenantId(UUID.randomUUID());
        a.setCompanyId(UUID.randomUUID());
        a.setStatus(AppraisalStatus.CALIBRATION);
        a.setSelfRating(new BigDecimal("4.00"));
        a.setManagerRating(new BigDecimal("3.75"));
        a.setReviewerRating(new BigDecimal("4.00"));
        a.setCalibratedRating(new BigDecimal("3.90"));
        a.setFinalBand("B");
        a.setIncrementRecommendation(IncrementRecommendation.STANDARD);
        a.setPromotionRecommendation(PromotionRecommendation.READY_NEXT_CYCLE);
        AppraisalResponse res = mapper.toResponse(a);
        assertThat(res.selfRating()).isEqualTo(new BigDecimal("4.00"));
        assertThat(res.calibratedRating()).isEqualTo(new BigDecimal("3.90"));
        assertThat(res.finalBand()).isEqualTo("B");
        assertThat(res.incrementRecommendation()).isEqualTo(IncrementRecommendation.STANDARD);
        assertThat(res.promotionRecommendation())
                .isEqualTo(PromotionRecommendation.READY_NEXT_CYCLE);
    }

    @Test
    void reportRowFallsBackWhenEmployeeMissing() {
        Appraisal a = new Appraisal();
        setId(a, UUID.randomUUID());
        a.setStatus(AppraisalStatus.PENDING_SELF);
        AppraisalReportRowResponse row = mapper.toReportRow(a);
        assertThat(row.employeeId()).isNull();
        assertThat(row.employeeName()).isNull();
        assertThat(row.employeeNumber()).isNull();
    }

    @Test
    void reportRowIncludesEmployeeSummary() {
        Employee e = new Employee();
        setId(e, UUID.randomUUID());
        e.setEmployeeNumber("E-2001");
        e.setFirstName("Marie");
        e.setLastName("Curie");
        Appraisal a = new Appraisal();
        setId(a, UUID.randomUUID());
        a.setEmployee(e);
        a.setStatus(AppraisalStatus.FINALISED);
        AppraisalReportRowResponse row = mapper.toReportRow(a);
        assertThat(row.employeeNumber()).isEqualTo("E-2001");
        assertThat(row.employeeName()).isEqualTo("Marie Curie");
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
