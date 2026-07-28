package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CompensationLineRequest;
import com.ewos.payroll.api.dto.CreateEmployeeCompensationRequest;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.PayComponent;
import com.ewos.payroll.domain.PayComponentCalculationType;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayGroup;
import com.ewos.payroll.domain.PayrollFrequency;
import com.ewos.payroll.infrastructure.persistence.EmployeeCompensationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * Effective-dated compensation creation — supersession, cross-company guards, LOP-line assembly.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeCompensationServiceTest {

    @Mock EmployeeCompensationRepository repository;
    @Mock EmployeeRepository employees;
    @Mock PayComponentService components;
    @Mock PayGroupService payGroups;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ClientAccessGuard guard;

    private EmployeeCompensationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new EmployeeCompensationService(
                        repository,
                        employees,
                        components,
                        payGroups,
                        new PayrollMapper(),
                        eventPublisher,
                        guard);
        org.mockito.Mockito.lenient()
                .when(repository.save(any(EmployeeCompensation.class)))
                .thenAnswer(
                        inv -> {
                            EmployeeCompensation c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
    }

    private Employee employeeIn(UUID company) {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setCompanyId(company);
        return e;
    }

    private CreateEmployeeCompensationRequest request(
            BigDecimal basic, List<CompensationLineRequest> lines) {
        return new CreateEmployeeCompensationRequest(
                tenantId,
                companyId,
                employeeId,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                PayrollFrequency.MONTHLY,
                basic,
                "USD",
                null,
                lines);
    }

    @Test
    void createChecksAccessForTheRequestedCompanyBeforeTouchingTheEmployeeRecord() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        when(repository.findActiveForEmployee(tenantId, employeeId)).thenReturn(Optional.empty());

        service.create(request(new BigDecimal("50000"), null));

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createRejectedWhenCallerLacksCompanyAccess() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(() -> service.create(request(new BigDecimal("50000"), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not authorized");
        verify(employees, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void createRejectedWhenEmployeeDoesNotExist() {
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(new BigDecimal("50000"), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Employee not found");
    }

    @Test
    void createRejectedWhenEmployeeBelongsToADifferentCompanyThanTheRequest() {
        UUID otherCompany = UUID.randomUUID();
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(otherCompany)));

        assertThatThrownBy(() -> service.create(request(new BigDecimal("50000"), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different company");
    }

    @Test
    void createRejectedWhenPayGroupBelongsToADifferentCompany() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        when(repository.findActiveForEmployee(tenantId, employeeId)).thenReturn(Optional.empty());
        UUID payGroupId = UUID.randomUUID();
        PayGroup group = new PayGroup();
        group.setId(payGroupId);
        group.setCompanyId(UUID.randomUUID());
        when(payGroups.require(tenantId, payGroupId)).thenReturn(group);

        CreateEmployeeCompensationRequest req =
                new CreateEmployeeCompensationRequest(
                        tenantId,
                        companyId,
                        employeeId,
                        payGroupId,
                        LocalDate.of(2026, 1, 1),
                        null,
                        PayrollFrequency.MONTHLY,
                        new BigDecimal("50000"),
                        "USD",
                        null,
                        null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Pay group belongs to a different company");
    }

    @Test
    void createSupersedesThePreviouslyActiveCompensationRecord() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        EmployeeCompensation previous = new EmployeeCompensation();
        previous.setId(UUID.randomUUID());
        previous.setActive(true);
        when(repository.findActiveForEmployee(tenantId, employeeId))
                .thenReturn(Optional.of(previous));

        service.create(request(new BigDecimal("60000"), null));

        assertThat(previous.isActive()).isFalse();
        assertThat(previous.getEffectiveTo()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void createDoesNotOverwriteAnExplicitEffectiveToOnThePreviousRecord() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        EmployeeCompensation previous = new EmployeeCompensation();
        previous.setId(UUID.randomUUID());
        previous.setActive(true);
        previous.setEffectiveTo(LocalDate.of(2025, 6, 30));
        when(repository.findActiveForEmployee(tenantId, employeeId))
                .thenReturn(Optional.of(previous));

        service.create(request(new BigDecimal("60000"), null));

        assertThat(previous.getEffectiveTo()).isEqualTo(LocalDate.of(2025, 6, 30));
    }

    @Test
    void createAttachesCompensationLinesResolvedThroughThePayComponentCatalogue() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        when(repository.findActiveForEmployee(tenantId, employeeId)).thenReturn(Optional.empty());
        UUID componentId = UUID.randomUUID();
        PayComponent hra = new PayComponent();
        hra.setId(componentId);
        hra.setCode("HRA");
        hra.setKind(PayComponentKind.EARNING);
        hra.setCalculationType(PayComponentCalculationType.PERCENT_OF_BASIC);
        when(components.require(tenantId, componentId)).thenReturn(hra);

        var response =
                service.create(
                        request(
                                new BigDecimal("60000"),
                                List.of(
                                        new CompensationLineRequest(
                                                componentId, null, new BigDecimal("40")))));

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).payComponentId()).isEqualTo(componentId);
    }

    @Test
    void requireActiveForEmployeeThrowsUnprocessableWhenNoneExists() {
        when(repository.findActiveForEmployee(tenantId, employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveForEmployee(tenantId, employeeId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void getByIdChecksAccessForTheRecordsCompanyNotTheCaller() {
        UUID id = UUID.randomUUID();
        EmployeeCompensation c = new EmployeeCompensation();
        c.setId(id);
        c.setCompanyId(companyId);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(c));

        service.getById(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void getByIdThrowsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void historyForEmployeeChecksAccessAcrossEveryDistinctCompanyReturned() {
        EmployeeCompensation a = new EmployeeCompensation();
        a.setCompanyId(companyId);
        UUID otherCompany = UUID.randomUUID();
        EmployeeCompensation b = new EmployeeCompensation();
        b.setCompanyId(otherCompany);
        when(repository.findHistoryForEmployee(tenantId, employeeId)).thenReturn(List.of(a, b));

        service.historyForEmployee(tenantId, employeeId);

        verify(guard).requireAccessForCompanies(List.of(companyId, otherCompany));
    }
}
