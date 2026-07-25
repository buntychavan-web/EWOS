package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.reports.ProviderDashboardResponse;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.infrastructure.persistence.PayrollPeriodRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.Company;
import com.ewos.tenancy.domain.ServiceOffering;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.ClientRepository;
import com.ewos.tenancy.infrastructure.persistence.CompanyRepository;
import com.ewos.tenancy.infrastructure.persistence.ServiceOfferingRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProviderDashboardServiceTest {

    @Mock ClientAccessGuard guard;
    @Mock ClientRepository clients;
    @Mock CompanyRepository companies;
    @Mock PayrollPeriodRepository periods;
    @Mock PayrollRunRepository runs;
    @Mock ServiceOfferingRepository services;

    private ProviderDashboardService service;

    @BeforeEach
    void setUp() {
        service =
                new ProviderDashboardService(
                        guard,
                        clients,
                        companies,
                        periods,
                        runs,
                        services,
                        new TenancyMapper(),
                        new PayrollMapper());
    }

    @Test
    void scopesEverythingToTheCallersAccessibleClients() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(guard.hasUnrestrictedAccess()).thenReturn(false);
        when(guard.accessibleClientIds()).thenReturn(Set.of(clientId));

        Client client = new Client();
        client.setId(clientId);
        client.setTenant(new Tenant());
        client.setCode("CL-01");
        client.setLegalName("Northwind");
        when(clients.findAllByIdInOrderByLegalNameAsc(List.of(clientId)))
                .thenReturn(List.of(client));

        Company company = new Company();
        company.setId(companyId);
        when(companies.findAllByClientIdInOrderByNameAsc(List.of(clientId)))
                .thenReturn(List.of(company));

        PayrollPeriod openPeriod = new PayrollPeriod();
        openPeriod.setId(UUID.randomUUID());
        openPeriod.setCompanyId(companyId);
        openPeriod.setStatus(PayrollPeriodStatus.OPEN);
        PayrollPeriod closedPeriod = new PayrollPeriod();
        closedPeriod.setId(UUID.randomUUID());
        closedPeriod.setCompanyId(companyId);
        closedPeriod.setStatus(PayrollPeriodStatus.CLOSED);
        when(periods.findAllByTenantIdAndCompanyIdInOrderByPayDateAsc(tenantId, List.of(companyId)))
                .thenReturn(List.of(openPeriod, closedPeriod));

        PayrollRun completedRun = new PayrollRun();
        completedRun.setId(UUID.randomUUID());
        completedRun.setCompanyId(companyId);
        completedRun.setStatus(PayrollRunStatus.COMPLETED);
        PayrollRun finalizedRun = new PayrollRun();
        finalizedRun.setId(UUID.randomUUID());
        finalizedRun.setCompanyId(companyId);
        finalizedRun.setStatus(PayrollRunStatus.FINALIZED);
        when(runs.findAllByTenantIdAndCompanyIdInOrderByCreatedAtDesc(tenantId, List.of(companyId)))
                .thenReturn(List.of(completedRun, finalizedRun));

        ServiceOffering active = new ServiceOffering();
        active.setId(UUID.randomUUID());
        active.setActive(true);
        ServiceOffering inactive = new ServiceOffering();
        inactive.setId(UUID.randomUUID());
        inactive.setActive(false);
        when(services.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId))
                .thenReturn(List.of(active, inactive));

        ProviderDashboardResponse r = service.getDashboard(tenantId);

        assertThat(r.assignedClients()).hasSize(1);
        assertThat(r.assignedClients().get(0).id()).isEqualTo(clientId);
        assertThat(r.activePayrollPeriods()).hasSize(1);
        assertThat(r.payrollCalendar()).hasSize(2);
        assertThat(r.payrollStatusCounts().get(PayrollRunStatus.COMPLETED)).isEqualTo(1L);
        assertThat(r.pendingApprovals()).hasSize(1);
        assertThat(r.pendingApprovals().get(0).status()).isEqualTo(PayrollRunStatus.COMPLETED);
        assertThat(r.activeServiceCount()).isEqualTo(1);
        assertThat(r.totalServiceCount()).isEqualTo(2);
    }

    @Test
    void unrestrictedAccessSeesEveryClientInTheTenant() {
        UUID tenantId = UUID.randomUUID();
        when(guard.hasUnrestrictedAccess()).thenReturn(true);
        when(clients.findAllByTenantIdOrderByLegalNameAsc(tenantId)).thenReturn(List.of());
        when(services.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId)).thenReturn(List.of());

        service.getDashboard(tenantId);

        org.mockito.Mockito.verify(guard, org.mockito.Mockito.never()).accessibleClientIds();
    }

    @Test
    void emptyClientSetShortCircuitsWithoutQueryingCompaniesOrPeriods() {
        UUID tenantId = UUID.randomUUID();
        when(guard.hasUnrestrictedAccess()).thenReturn(false);
        when(guard.accessibleClientIds()).thenReturn(Set.of());
        when(clients.findAllByIdInOrderByLegalNameAsc(List.of())).thenReturn(List.of());
        when(services.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId)).thenReturn(List.of());

        ProviderDashboardResponse r = service.getDashboard(tenantId);

        assertThat(r.assignedClients()).isEmpty();
        assertThat(r.activePayrollPeriods()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(companies, periods, runs);
    }
}
