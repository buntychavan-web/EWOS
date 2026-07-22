package com.ewos.succession.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.succession.api.dto.CareerPathResponse;
import com.ewos.succession.api.dto.EligibilityResponse;
import com.ewos.succession.api.dto.PoolResponse;
import com.ewos.succession.api.dto.ReadinessResponse;
import com.ewos.succession.domain.CareerPath;
import com.ewos.succession.domain.PromotionEligibility;
import com.ewos.succession.domain.ReadinessAssessment;
import com.ewos.succession.domain.ReadinessLevel;
import com.ewos.succession.domain.TalentPool;
import com.ewos.succession.domain.TalentTier;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SuccessionMapperTest {

    private final SuccessionMapper mapper = new SuccessionMapper();

    @Test
    void careerPathRoundTrip() {
        CareerPath c = new CareerPath();
        setId(c, UUID.randomUUID());
        c.setTenantId(UUID.randomUUID());
        c.setCompanyId(UUID.randomUUID());
        c.setCode("ENG-SR");
        c.setName("Engineer → Senior Engineer");
        c.setFromDesignation("Engineer");
        c.setToDesignation("Senior Engineer");
        c.setMinTenureMonths(24);
        c.setActive(true);
        CareerPathResponse res = mapper.toResponse(c);
        assertThat(res.fromDesignation()).isEqualTo("Engineer");
        assertThat(res.minTenureMonths()).isEqualTo(24);
    }

    @Test
    void eligibilityRoundTrip() {
        PromotionEligibility e = new PromotionEligibility();
        setId(e, UUID.randomUUID());
        e.setEligible(true);
        e.setTenureMonths(30);
        e.setLastRating(new BigDecimal("4.20"));
        EligibilityResponse res = mapper.toResponse(e);
        assertThat(res.eligible()).isTrue();
        assertThat(res.lastRating()).isEqualTo(new BigDecimal("4.20"));
    }

    @Test
    void poolRoundTrip() {
        TalentPool p = new TalentPool();
        setId(p, UUID.randomUUID());
        p.setTenantId(UUID.randomUUID());
        p.setCompanyId(UUID.randomUUID());
        p.setCode("HIPO");
        p.setName("HiPo Pool");
        p.setTier(TalentTier.HIGH_POTENTIAL);
        p.setActive(true);
        PoolResponse res = mapper.toResponse(p);
        assertThat(res.tier()).isEqualTo(TalentTier.HIGH_POTENTIAL);
        assertThat(res.code()).isEqualTo("HIPO");
    }

    @Test
    void readinessRoundTrip() {
        ReadinessAssessment r = new ReadinessAssessment();
        setId(r, UUID.randomUUID());
        r.setTier(TalentTier.HIGH_PERFORMER);
        r.setReadiness(ReadinessLevel.READY_1_YEAR);
        r.setPerformanceScore(new BigDecimal("4.5"));
        ReadinessResponse res = mapper.toResponse(r);
        assertThat(res.tier()).isEqualTo(TalentTier.HIGH_PERFORMER);
        assertThat(res.readiness()).isEqualTo(ReadinessLevel.READY_1_YEAR);
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
