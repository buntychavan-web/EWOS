package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.api.EmployeeMapper;
import com.ewos.employee.api.dto.EmployeeResponse;
import com.ewos.employee.api.dto.HireEmployeeRequest;
import com.ewos.employee.api.dto.TerminateEmployeeRequest;
import com.ewos.employee.api.dto.UpdateEmployeeRequest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeLifecyclePolicy;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.domain.EmploymentType;
import com.ewos.employee.domain.events.EmployeeEvent;
import com.ewos.employee.domain.events.EmployeeEventType;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.employee.infrastructure.persistence.EmploymentTypeRepository;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.shared.exception.ApiException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock EmployeeRepository employees;
    @Mock EmploymentTypeRepository employmentTypes;
    @Mock OrganizationUnitRepository orgUnits;
    @Mock ApplicationEventPublisher events;

    private EmployeeService service;
    private final EmployeeLifecyclePolicy policy = new EmployeeLifecyclePolicy();

    @BeforeEach
    void setUp() {
        service =
                new EmployeeService(
                        employees, employmentTypes, orgUnits, policy, new EmployeeMapper(), events);
        lenient()
                .when(employees.save(any(Employee.class)))
                .thenAnswer(
                        inv -> {
                            Employee e = inv.getArgument(0);
                            if (e.getId() == null) {
                                e.setId(UUID.randomUUID());
                            }
                            return e;
                        });
    }

    @Test
    void hireCreatesActiveEmployeeAndEmitsHiredEvent() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        EmploymentType type = new EmploymentType();
        type.setId(typeId);
        type.setCode("FULL_TIME");
        OrganizationUnit unit = new OrganizationUnit();
        unit.setId(unitId);
        unit.setTenantId(tenant);
        unit.setCompanyId(company);

        when(employmentTypes.findByIdAndTenantId(typeId, tenant)).thenReturn(Optional.of(type));
        when(orgUnits.findByIdAndTenantId(unitId, tenant)).thenReturn(Optional.of(unit));

        EmployeeResponse r =
                service.hire(
                        new HireEmployeeRequest(
                                tenant,
                                company,
                                null,
                                "EMP-1",
                                "Alice",
                                null,
                                "Smith",
                                null,
                                "alice@ex.com",
                                null,
                                null,
                                null,
                                null,
                                unitId,
                                null,
                                typeId,
                                LocalDate.of(2024, 6, 1)));

        assertThat(r.status()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(r.employeeNumber()).isEqualTo("EMP-1");
        assertThat(r.displayName()).isEqualTo("Alice Smith");

        ArgumentCaptor<EmployeeEvent> ev = ArgumentCaptor.forClass(EmployeeEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().eventType()).isEqualTo(EmployeeEventType.HIRED);
    }

    @Test
    void hireRejectsDuplicateEmployeeNumber() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(employees.existsByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenant, company, "DUP"))
                .thenReturn(true);
        assertThatThrownBy(
                        () ->
                                service.hire(
                                        new HireEmployeeRequest(
                                                tenant,
                                                company,
                                                null,
                                                "DUP",
                                                "A",
                                                null,
                                                "B",
                                                null,
                                                "a@b.com",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                LocalDate.now())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Employee number");
    }

    @Test
    void terminateSetsStatusAndDate() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        Employee e = fixture(tenant, company);
        e.setHireDate(LocalDate.of(2024, 1, 1));
        when(employees.findByIdAndTenantId(e.getId(), tenant)).thenReturn(Optional.of(e));

        service.terminate(
                tenant,
                e.getId(),
                new TerminateEmployeeRequest(LocalDate.of(2024, 12, 31), "End of contract"));

        assertThat(e.getStatus()).isEqualTo(EmployeeStatus.TERMINATED);
        assertThat(e.getTerminationDate()).isEqualTo(LocalDate.of(2024, 12, 31));

        ArgumentCaptor<EmployeeEvent> ev = ArgumentCaptor.forClass(EmployeeEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().eventType()).isEqualTo(EmployeeEventType.TERMINATED);
    }

    @Test
    void updateRejectsTerminatedEmployee() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        Employee e = fixture(tenant, company);
        e.setStatus(EmployeeStatus.TERMINATED);
        when(employees.findByIdAndTenantId(e.getId(), tenant)).thenReturn(Optional.of(e));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        tenant,
                                        e.getId(),
                                        new UpdateEmployeeRequest(
                                                "New", null, null, null, null, null, null, null,
                                                null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("terminated");
    }

    @Test
    void deleteRejectsWhenDirectReportsExist() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        Employee e = fixture(tenant, company);
        when(employees.findByIdAndTenantId(e.getId(), tenant)).thenReturn(Optional.of(e));
        when(employees.countDirectReports(e.getId())).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(tenant, e.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("3 direct reports");
    }

    @Test
    void changeStatusRejectsMovingToTerminatedDirectly() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        Employee e = fixture(tenant, company);
        when(employees.findByIdAndTenantId(e.getId(), tenant)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.changeStatus(tenant, e.getId(), EmployeeStatus.TERMINATED))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("terminate");
    }

    private Employee fixture(UUID tenant, UUID company) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenant);
        e.setCompanyId(company);
        e.setEmployeeNumber("EMP-100");
        e.setFirstName("Alice");
        e.setLastName("Smith");
        e.setWorkEmail("a@ex.com");
        e.setHireDate(LocalDate.of(2024, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        return e;
    }
}
