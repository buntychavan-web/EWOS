package com.ewos.employee.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.employee.api.dto.EmployeeResponse;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.domain.EmploymentType;
import com.ewos.organization.domain.OrganizationUnit;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployeeMapperTest {

    private final EmployeeMapper mapper = new EmployeeMapper();

    @Test
    void mapsAllFields() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        OrganizationUnit unit = new OrganizationUnit();
        unit.setId(UUID.randomUUID());
        unit.setCode("ENG");

        EmploymentType type = new EmploymentType();
        type.setId(UUID.randomUUID());
        type.setCode("FULL_TIME");

        Employee manager = new Employee();
        manager.setId(UUID.randomUUID());

        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setCompanyId(companyId);
        e.setPersonId(UUID.randomUUID());
        e.setEmployeeNumber("EMP-000042");
        e.setFirstName("Alice");
        e.setLastName("Smith");
        e.setDisplayName("Alice Smith");
        e.setWorkEmail("alice@ex.com");
        e.setHireDate(LocalDate.of(2024, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setPrimaryOrgUnit(unit);
        e.setEmploymentType(type);
        e.setManager(manager);

        EmployeeResponse r = mapper.toResponse(e);

        assertThat(r.tenantId()).isEqualTo(tenantId);
        assertThat(r.companyId()).isEqualTo(companyId);
        assertThat(r.employeeNumber()).isEqualTo("EMP-000042");
        assertThat(r.workEmail()).isEqualTo("alice@ex.com");
        assertThat(r.primaryOrgUnitId()).isEqualTo(unit.getId());
        assertThat(r.primaryOrgUnitCode()).isEqualTo("ENG");
        assertThat(r.employmentTypeCode()).isEqualTo("FULL_TIME");
        assertThat(r.managerEmployeeId()).isEqualTo(manager.getId());
        assertThat(r.status()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void mapsMinimalEmployeeWithoutRelations() {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setTenantId(UUID.randomUUID());
        e.setCompanyId(UUID.randomUUID());
        e.setEmployeeNumber("EMP-1");
        e.setFirstName("A");
        e.setLastName("B");
        e.setWorkEmail("a@b.com");
        e.setHireDate(LocalDate.now());
        e.setStatus(EmployeeStatus.ACTIVE);

        EmployeeResponse r = mapper.toResponse(e);

        assertThat(r.primaryOrgUnitId()).isNull();
        assertThat(r.primaryOrgUnitCode()).isNull();
        assertThat(r.managerEmployeeId()).isNull();
        assertThat(r.employmentTypeId()).isNull();
    }
}
