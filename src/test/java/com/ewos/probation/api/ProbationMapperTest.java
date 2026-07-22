package com.ewos.probation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.employee.domain.Employee;
import com.ewos.probation.api.dto.ProbationPolicyResponse;
import com.ewos.probation.api.dto.ProbationRecordResponse;
import com.ewos.probation.api.dto.ProbationReportRowResponse;
import com.ewos.probation.domain.HrRecommendation;
import com.ewos.probation.domain.ProbationPolicy;
import com.ewos.probation.domain.ProbationRecord;
import com.ewos.probation.domain.ProbationStatus;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProbationMapperTest {

    private final ProbationMapper mapper = new ProbationMapper();

    @Test
    void policyRoundTrip() {
        ProbationPolicy p = new ProbationPolicy();
        setId(p, UUID.randomUUID());
        p.setTenantId(UUID.randomUUID());
        p.setCompanyId(UUID.randomUUID());
        p.setCode("STD90");
        p.setName("Standard 90-day");
        p.setDescription("Default probation");
        p.setDefaultPeriodDays(90);
        p.setMaxExtensionDays(60);
        p.setAllowEarlyConfirm(true);
        p.setActive(true);
        ProbationPolicyResponse res = mapper.toResponse(p);
        assertThat(res.code()).isEqualTo("STD90");
        assertThat(res.defaultPeriodDays()).isEqualTo(90);
        assertThat(res.maxExtensionDays()).isEqualTo(60);
        assertThat(res.allowEarlyConfirm()).isTrue();
        assertThat(res.active()).isTrue();
    }

    @Test
    void recordResponseIncludesEffectiveEnd() {
        ProbationRecord r = new ProbationRecord();
        setId(r, UUID.randomUUID());
        r.setTenantId(UUID.randomUUID());
        r.setCompanyId(UUID.randomUUID());
        r.setPeriodStart(LocalDate.of(2026, 4, 1));
        r.setPeriodEnd(LocalDate.of(2026, 7, 1));
        r.setExtendedEnd(LocalDate.of(2026, 8, 15));
        r.setStatus(ProbationStatus.EXTENDED);
        r.setHrRecommendation(HrRecommendation.CONFIRM);
        ProbationRecordResponse res = mapper.toResponse(r);
        assertThat(res.effectiveEnd()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(res.status()).isEqualTo(ProbationStatus.EXTENDED);
        assertThat(res.hrRecommendation()).isEqualTo(HrRecommendation.CONFIRM);
        assertThat(res.employeeId()).isNull();
        assertThat(res.policyId()).isNull();
    }

    @Test
    void reportRowFallsBackWhenEmployeeMissing() {
        ProbationRecord r = new ProbationRecord();
        setId(r, UUID.randomUUID());
        r.setPeriodStart(LocalDate.of(2026, 4, 1));
        r.setPeriodEnd(LocalDate.of(2026, 7, 1));
        r.setStatus(ProbationStatus.IN_PROBATION);
        ProbationReportRowResponse row = mapper.toReportRow(r);
        assertThat(row.employeeId()).isNull();
        assertThat(row.employeeName()).isNull();
        assertThat(row.employeeNumber()).isNull();
        assertThat(row.effectiveEnd()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void reportRowIncludesEmployeeSummary() {
        Employee e = new Employee();
        setId(e, UUID.randomUUID());
        e.setEmployeeNumber("E-1001");
        e.setFirstName("Ada");
        e.setLastName("Lovelace");
        ProbationRecord r = new ProbationRecord();
        setId(r, UUID.randomUUID());
        r.setEmployee(e);
        r.setPeriodStart(LocalDate.of(2026, 4, 1));
        r.setPeriodEnd(LocalDate.of(2026, 7, 1));
        r.setStatus(ProbationStatus.IN_PROBATION);
        ProbationReportRowResponse row = mapper.toReportRow(r);
        assertThat(row.employeeNumber()).isEqualTo("E-1001");
        assertThat(row.employeeName()).isEqualTo("Ada Lovelace");
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
