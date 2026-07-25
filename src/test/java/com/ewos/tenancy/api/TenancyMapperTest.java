package com.ewos.tenancy.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.tenancy.api.dto.ClientAssignmentResponse;
import com.ewos.tenancy.api.dto.ClientResponse;
import com.ewos.tenancy.api.dto.CompanyResponse;
import com.ewos.tenancy.api.dto.PayrollServiceProviderResponse;
import com.ewos.tenancy.api.dto.ServiceOfferingResponse;
import com.ewos.tenancy.api.dto.TenantResponse;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.ClientAssignment;
import com.ewos.tenancy.domain.ClientAssignmentScopeRole;
import com.ewos.tenancy.domain.ClientStatus;
import com.ewos.tenancy.domain.Company;
import com.ewos.tenancy.domain.CompanyStatus;
import com.ewos.tenancy.domain.PayrollServiceProvider;
import com.ewos.tenancy.domain.ProviderStatus;
import com.ewos.tenancy.domain.ServiceOffering;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.domain.TenantStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenancyMapperTest {

    private final TenancyMapper mapper = new TenancyMapper();

    @Test
    void tenantMapsAllFields() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setCode("ACME");
        tenant.setName("Acme Payroll Outsourcing");
        tenant.setStatus(TenantStatus.ACTIVE);

        TenantResponse r = mapper.toResponse(tenant);

        assertThat(r.id()).isEqualTo(tenant.getId());
        assertThat(r.code()).isEqualTo("ACME");
        assertThat(r.name()).isEqualTo("Acme Payroll Outsourcing");
        assertThat(r.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void clientMapsAllFieldsIncludingTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setTenant(tenant);
        client.setCode("CL-01");
        client.setLegalName("Northwind Traders");
        client.setStatus(ClientStatus.ACTIVE);
        client.setOnboardedAt(LocalDate.of(2026, 1, 1));

        ClientResponse r = mapper.toResponse(client);

        assertThat(r.tenantId()).isEqualTo(tenant.getId());
        assertThat(r.code()).isEqualTo("CL-01");
        assertThat(r.legalName()).isEqualTo("Northwind Traders");
        assertThat(r.status()).isEqualTo(ClientStatus.ACTIVE);
        assertThat(r.onboardedAt()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void companyMapsAllFieldsIncludingClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());

        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setTenantId(UUID.randomUUID());
        company.setClient(client);
        company.setCode("CO-IN");
        company.setName("Northwind India Pvt Ltd");
        company.setCountryCode("IN");
        company.setStatus(CompanyStatus.ACTIVE);

        CompanyResponse r = mapper.toResponse(company);

        assertThat(r.clientId()).isEqualTo(client.getId());
        assertThat(r.code()).isEqualTo("CO-IN");
        assertThat(r.countryCode()).isEqualTo("IN");
        assertThat(r.status()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    void serviceOfferingMapsAllFields() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        ServiceOffering service = new ServiceOffering();
        service.setId(UUID.randomUUID());
        service.setTenant(tenant);
        service.setCode("PAYROLL_PROCESSING");
        service.setName("Payroll Processing");
        service.setCategory("PAYROLL");
        service.setSortOrder(10);
        service.setActive(true);

        ServiceOfferingResponse r = mapper.toResponse(service);

        assertThat(r.tenantId()).isEqualTo(tenant.getId());
        assertThat(r.code()).isEqualTo("PAYROLL_PROCESSING");
        assertThat(r.category()).isEqualTo("PAYROLL");
        assertThat(r.sortOrder()).isEqualTo(10);
        assertThat(r.active()).isTrue();
    }

    @Test
    void providerMapsAllFields() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        PayrollServiceProvider provider = new PayrollServiceProvider();
        provider.setId(UUID.randomUUID());
        provider.setTenant(tenant);
        provider.setCode("PROV-01");
        provider.setName("Acme Payroll Services");
        provider.setStatus(ProviderStatus.ACTIVE);

        PayrollServiceProviderResponse r = mapper.toResponse(provider);

        assertThat(r.tenantId()).isEqualTo(tenant.getId());
        assertThat(r.code()).isEqualTo("PROV-01");
        assertThat(r.status()).isEqualTo(ProviderStatus.ACTIVE);
    }

    @Test
    void clientAssignmentMapsAllFieldsIncludingNullableService() {
        PayrollServiceProvider provider = new PayrollServiceProvider();
        provider.setId(UUID.randomUUID());
        Client client = new Client();
        client.setId(UUID.randomUUID());
        UUID userId = UUID.randomUUID();

        ClientAssignment assignment = new ClientAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setProvider(provider);
        assignment.setUserId(userId);
        assignment.setClient(client);
        assignment.setService(null);
        assignment.setScopeRole(ClientAssignmentScopeRole.PAYROLL_ADMIN);
        assignment.setActive(true);
        assignment.setEffectiveFrom(LocalDate.of(2026, 1, 1));

        ClientAssignmentResponse r = mapper.toResponse(assignment);

        assertThat(r.providerId()).isEqualTo(provider.getId());
        assertThat(r.userId()).isEqualTo(userId);
        assertThat(r.clientId()).isEqualTo(client.getId());
        assertThat(r.serviceId()).isNull();
        assertThat(r.scopeRole()).isEqualTo(ClientAssignmentScopeRole.PAYROLL_ADMIN);
        assertThat(r.active()).isTrue();

        ServiceOffering service = new ServiceOffering();
        service.setId(UUID.randomUUID());
        assignment.setService(service);
        assertThat(mapper.toResponse(assignment).serviceId()).isEqualTo(service.getId());
    }
}
