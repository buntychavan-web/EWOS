package com.ewos.learning.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.learning.api.dto.CertificationResponse;
import com.ewos.learning.api.dto.CourseResponse;
import com.ewos.learning.api.dto.EnrollmentResponse;
import com.ewos.learning.api.dto.LearningPathResponse;
import com.ewos.learning.api.dto.SessionResponse;
import com.ewos.learning.domain.Certification;
import com.ewos.learning.domain.CertificationStatus;
import com.ewos.learning.domain.DeliveryMode;
import com.ewos.learning.domain.EnrollmentStatus;
import com.ewos.learning.domain.LearningPath;
import com.ewos.learning.domain.TrainingCourse;
import com.ewos.learning.domain.TrainingEnrollment;
import com.ewos.learning.domain.TrainingSession;
import com.ewos.learning.domain.TrainingSessionStatus;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LearningMapperTest {

    private final LearningMapper mapper = new LearningMapper();

    @Test
    void courseRoundTrip() {
        TrainingCourse c = new TrainingCourse();
        setId(c, UUID.randomUUID());
        c.setTenantId(UUID.randomUUID());
        c.setCompanyId(UUID.randomUUID());
        c.setCode("SEC-101");
        c.setName("Security Awareness");
        c.setDeliveryMode(DeliveryMode.ONLINE);
        c.setDurationHours(new BigDecimal("2.00"));
        c.setCertificationOffered(true);
        c.setCertificationValidDays(365);
        c.setActive(true);
        CourseResponse res = mapper.toResponse(c);
        assertThat(res.deliveryMode()).isEqualTo(DeliveryMode.ONLINE);
        assertThat(res.certificationOffered()).isTrue();
        assertThat(res.certificationValidDays()).isEqualTo(365);
    }

    @Test
    void pathRoundTrip() {
        LearningPath p = new LearningPath();
        setId(p, UUID.randomUUID());
        p.setTenantId(UUID.randomUUID());
        p.setCompanyId(UUID.randomUUID());
        p.setCode("NEW-ENG");
        p.setName("New Engineer Onboarding");
        p.setActive(true);
        LearningPathResponse res = mapper.toResponse(p);
        assertThat(res.code()).isEqualTo("NEW-ENG");
        assertThat(res.active()).isTrue();
    }

    @Test
    void sessionRoundTrip() {
        TrainingSession s = new TrainingSession();
        setId(s, UUID.randomUUID());
        s.setTenantId(UUID.randomUUID());
        s.setCompanyId(UUID.randomUUID());
        s.setName("Live Session Q3");
        s.setStartsAt(Instant.parse("2026-08-15T09:00:00Z"));
        s.setEndsAt(Instant.parse("2026-08-15T17:00:00Z"));
        s.setStatus(TrainingSessionStatus.SCHEDULED);
        SessionResponse res = mapper.toResponse(s);
        assertThat(res.status()).isEqualTo(TrainingSessionStatus.SCHEDULED);
        assertThat(res.startsAt()).isEqualTo(Instant.parse("2026-08-15T09:00:00Z"));
    }

    @Test
    void enrollmentRoundTrip() {
        TrainingEnrollment e = new TrainingEnrollment();
        setId(e, UUID.randomUUID());
        e.setTenantId(UUID.randomUUID());
        e.setCompanyId(UUID.randomUUID());
        e.setStatus(EnrollmentStatus.IN_PROGRESS);
        e.setAttendancePercent(new BigDecimal("87.50"));
        e.setAssessmentScore(new BigDecimal("92.00"));
        e.setPassed(Boolean.TRUE);
        EnrollmentResponse res = mapper.toResponse(e);
        assertThat(res.status()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        assertThat(res.attendancePercent()).isEqualTo(new BigDecimal("87.50"));
        assertThat(res.passed()).isTrue();
    }

    @Test
    void certificationRoundTrip() {
        Certification c = new Certification();
        setId(c, UUID.randomUUID());
        c.setTenantId(UUID.randomUUID());
        c.setCompanyId(UUID.randomUUID());
        c.setCertificationName("AWS SAA");
        c.setIssuingBody("AWS");
        c.setIssuedAt(LocalDate.of(2026, 4, 1));
        c.setExpiresAt(LocalDate.of(2028, 4, 1));
        c.setStatus(CertificationStatus.ACTIVE);
        CertificationResponse res = mapper.toResponse(c);
        assertThat(res.certificationName()).isEqualTo("AWS SAA");
        assertThat(res.status()).isEqualTo(CertificationStatus.ACTIVE);
        assertThat(res.expiresAt()).isEqualTo(LocalDate.of(2028, 4, 1));
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
