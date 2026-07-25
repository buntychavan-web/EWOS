package com.ewos.payroll.application;

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
import com.ewos.tenancy.infrastructure.persistence.ClientRepository;
import com.ewos.tenancy.infrastructure.persistence.CompanyRepository;
import com.ewos.tenancy.infrastructure.persistence.ServiceOfferingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the Payroll Service Provider's book of business for the current user: every client
 * they're assigned to (via {@link ClientAccessGuard}), the companies under those clients, and the
 * payroll operational data for those companies. The result set is built entirely from the caller's
 * own accessible-client set, so it is Chinese-Wall-safe by construction — there is no separate
 * per-row guard check to remember.
 */
@Service
@Transactional(readOnly = true)
public class ProviderDashboardService {

    private final ClientAccessGuard guard;
    private final ClientRepository clients;
    private final CompanyRepository companies;
    private final PayrollPeriodRepository periods;
    private final PayrollRunRepository runs;
    private final ServiceOfferingRepository services;
    private final TenancyMapper tenancyMapper;
    private final PayrollMapper payrollMapper;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public ProviderDashboardService(
            ClientAccessGuard guard,
            ClientRepository clients,
            CompanyRepository companies,
            PayrollPeriodRepository periods,
            PayrollRunRepository runs,
            ServiceOfferingRepository services,
            TenancyMapper tenancyMapper,
            PayrollMapper payrollMapper) {
        this.guard = guard;
        this.clients = clients;
        this.companies = companies;
        this.periods = periods;
        this.runs = runs;
        this.services = services;
        this.tenancyMapper = tenancyMapper;
        this.payrollMapper = payrollMapper;
    }

    public ProviderDashboardResponse getDashboard(UUID tenantId) {
        List<Client> assignedClients =
                guard.hasUnrestrictedAccess()
                        ? clients.findAllByTenantIdOrderByLegalNameAsc(tenantId)
                        : clients.findAllByIdInOrderByLegalNameAsc(
                                new ArrayList<>(guard.accessibleClientIds()));

        List<UUID> clientIds = assignedClients.stream().map(Client::getId).toList();
        List<Company> scopedCompanies =
                clientIds.isEmpty()
                        ? List.of()
                        : companies.findAllByClientIdInOrderByNameAsc(clientIds);
        List<UUID> companyIds = scopedCompanies.stream().map(Company::getId).toList();

        List<PayrollPeriod> allPeriods =
                companyIds.isEmpty()
                        ? List.of()
                        : periods.findAllByTenantIdAndCompanyIdInOrderByPayDateAsc(
                                tenantId, companyIds);
        List<PayrollPeriod> activePeriods =
                allPeriods.stream()
                        .filter(
                                p ->
                                        p.getStatus() == PayrollPeriodStatus.OPEN
                                                || p.getStatus() == PayrollPeriodStatus.LOCKED)
                        .toList();

        List<PayrollRun> allRuns =
                companyIds.isEmpty()
                        ? List.of()
                        : runs.findAllByTenantIdAndCompanyIdInOrderByCreatedAtDesc(
                                tenantId, companyIds);
        Map<PayrollRunStatus, Long> statusCounts =
                allRuns.stream()
                        .collect(
                                Collectors.groupingBy(
                                        PayrollRun::getStatus, Collectors.counting()));
        List<PayrollRun> pendingApprovals =
                allRuns.stream().filter(r -> r.getStatus() == PayrollRunStatus.COMPLETED).toList();

        List<ServiceOffering> catalogue =
                services.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId);
        long activeServiceCount = catalogue.stream().filter(ServiceOffering::isActive).count();

        return new ProviderDashboardResponse(
                assignedClients.stream().map(tenancyMapper::toResponse).toList(),
                activePeriods.stream().map(payrollMapper::toResponse).toList(),
                statusCounts,
                pendingApprovals.stream().map(payrollMapper::toResponse).toList(),
                allPeriods.stream().map(payrollMapper::toResponse).toList(),
                activeServiceCount,
                catalogue.size());
    }
}
