package com.ewos.payroll.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.payroll.api.dto.EmployeePayrollProfileResponse;
import com.ewos.payroll.api.dto.PayGroupResponse;
import com.ewos.payroll.api.dto.StatutorySettingResponse;
import com.ewos.payroll.domain.EmployeePayrollProfile;
import com.ewos.payroll.domain.PayGroup;
import com.ewos.payroll.domain.PayrollFrequency;
import com.ewos.payroll.domain.StatutorySetting;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayrollMapperExtraTest {

    private final PayrollMapper mapper = new PayrollMapper();

    @Test
    void mapsPayGroup() {
        PayGroup g = new PayGroup();
        g.setTenantId(UUID.randomUUID());
        g.setCompanyId(UUID.randomUUID());
        g.setCode("EXEC");
        g.setName("Executives");
        g.setFrequency(PayrollFrequency.MONTHLY);
        g.setCurrency("USD");
        g.setPayDayOfMonth(25);
        g.setActive(true);

        PayGroupResponse dto = mapper.toResponse(g);
        assertThat(dto.code()).isEqualTo("EXEC");
        assertThat(dto.frequency()).isEqualTo(PayrollFrequency.MONTHLY);
        assertThat(dto.payDayOfMonth()).isEqualTo(25);
    }

    @Test
    void mapsStatutorySetting() {
        StatutorySetting s = new StatutorySetting();
        s.setTenantId(UUID.randomUUID());
        s.setJurisdiction("IN");
        s.setCode("PF_EMPLOYER_PCT");
        s.setName("PF Employer Contribution %");
        s.setValueNumeric(new BigDecimal("12.000000"));
        s.setEffectiveFrom(LocalDate.of(2026, 4, 1));
        s.setActive(true);

        StatutorySettingResponse dto = mapper.toResponse(s);
        assertThat(dto.jurisdiction()).isEqualTo("IN");
        assertThat(dto.code()).isEqualTo("PF_EMPLOYER_PCT");
        assertThat(dto.valueNumeric()).isEqualByComparingTo("12.000000");
    }

    @Test
    void writeAndReadIdentifiersRoundtrips() {
        Map<String, String> ids = Map.of("PAN", "ABCDE1234F", "UAN", "100100100100");
        String json = PayrollMapper.writeIdentifiers(ids);
        assertThat(json).contains("PAN").contains("ABCDE1234F");

        EmployeePayrollProfile p = new EmployeePayrollProfile();
        p.setTenantId(UUID.randomUUID());
        p.setCompanyId(UUID.randomUUID());
        p.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        p.setCountryCode("IN");
        p.setStatutoryIdentifiersJson(json);

        EmployeePayrollProfileResponse dto = mapper.toResponse(p);
        assertThat(dto.statutoryIdentifiers()).containsEntry("PAN", "ABCDE1234F");
        assertThat(dto.statutoryIdentifiers()).containsEntry("UAN", "100100100100");
    }

    @Test
    void writeIdentifiersHandlesEmpty() {
        assertThat(PayrollMapper.writeIdentifiers(null)).isEqualTo("{}");
        assertThat(PayrollMapper.writeIdentifiers(Map.of())).isEqualTo("{}");
    }
}
