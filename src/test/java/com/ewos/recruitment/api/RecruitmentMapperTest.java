package com.ewos.recruitment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.recruitment.api.dto.JobPositionResponse;
import com.ewos.recruitment.api.dto.JobRequisitionResponse;
import com.ewos.recruitment.domain.EmploymentType;
import com.ewos.recruitment.domain.JobPosition;
import com.ewos.recruitment.domain.JobRequisition;
import com.ewos.recruitment.domain.RequisitionPriority;
import com.ewos.recruitment.domain.RequisitionStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecruitmentMapperTest {

    private final RecruitmentMapper mapper = new RecruitmentMapper();

    @Test
    void mapsJobPositionFields() {
        JobPosition p = new JobPosition();
        p.setTenantId(UUID.randomUUID());
        p.setCompanyId(UUID.randomUUID());
        p.setCode("SWE-01");
        p.setTitle("Software Engineer");
        p.setDescription("Builds things");
        p.setLocation("Bengaluru");
        p.setEmploymentType(EmploymentType.FULL_TIME);
        p.setGrade("L3");
        p.setSalaryCurrency("INR");
        p.setSalaryMin(new BigDecimal("500000.00"));
        p.setSalaryMax(new BigDecimal("1200000.00"));
        p.setActive(true);

        JobPositionResponse resp = mapper.toResponse(p);

        assertThat(resp.code()).isEqualTo("SWE-01");
        assertThat(resp.title()).isEqualTo("Software Engineer");
        assertThat(resp.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(resp.salaryCurrency()).isEqualTo("INR");
        assertThat(resp.salaryMin()).isEqualByComparingTo("500000.00");
        assertThat(resp.salaryMax()).isEqualByComparingTo("1200000.00");
        assertThat(resp.departmentOrgUnitId()).isNull();
        assertThat(resp.active()).isTrue();
    }

    @Test
    void mapsJobRequisitionFields() {
        JobPosition pos = new JobPosition();
        pos.setId(UUID.randomUUID());

        JobRequisition r = new JobRequisition();
        r.setTenantId(UUID.randomUUID());
        r.setCompanyId(UUID.randomUUID());
        r.setRequisitionNumber("REQ-2026-001");
        r.setJobPosition(pos);
        r.setTitle("Senior Engineer");
        r.setEmploymentType(EmploymentType.FULL_TIME);
        r.setHeadcount(3);
        r.setFilledCount(1);
        r.setPriority(RequisitionPriority.HIGH);
        r.setStatus(RequisitionStatus.OPEN);
        r.setBudgetCurrency("INR");
        r.setBudgetAmount(new BigDecimal("3600000.00"));

        JobRequisitionResponse resp = mapper.toResponse(r);

        assertThat(resp.requisitionNumber()).isEqualTo("REQ-2026-001");
        assertThat(resp.jobPositionId()).isEqualTo(pos.getId());
        assertThat(resp.headcount()).isEqualTo(3);
        assertThat(resp.filledCount()).isEqualTo(1);
        assertThat(resp.priority()).isEqualTo(RequisitionPriority.HIGH);
        assertThat(resp.status()).isEqualTo(RequisitionStatus.OPEN);
        assertThat(resp.budgetAmount()).isEqualByComparingTo("3600000.00");
        assertThat(resp.hiringManagerId()).isNull();
        assertThat(resp.recruiterId()).isNull();
    }
}
