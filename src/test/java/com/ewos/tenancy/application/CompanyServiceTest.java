package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CompanyResponse;
import com.ewos.tenancy.api.dto.CreateCompanyRequest;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.Company;
import com.ewos.tenancy.infrastructure.persistence.ClientRepository;
import com.ewos.tenancy.infrastructure.persistence.CompanyRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock CompanyRepository repo;
    @Mock ClientRepository clientRepo;
    @Mock ClientAccessGuard guard;

    private CompanyService service;

    @BeforeEach
    void setUp() {
        service = new CompanyService(repo, clientRepo, guard, new TenancyMapper());
        org.mockito.Mockito.lenient()
                .when(repo.save(any(Company.class)))
                .thenAnswer(
                        inv -> {
                            Company c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
    }

    @Test
    void createChecksClientAccessAndUniqueness() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Client client = new Client();
        client.setId(clientId);
        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(repo.existsByClientIdAndCodeIgnoreCase(clientId, "CO-IN")).thenReturn(false);

        CompanyResponse r =
                service.create(
                        new CreateCompanyRequest(
                                tenantId, clientId, "CO-IN", "India Pvt Ltd", "IN"));

        assertThat(r.code()).isEqualTo("CO-IN");
        assertThat(r.countryCode()).isEqualTo("IN");
        org.mockito.Mockito.verify(guard).requireAccess(clientId);
    }

    @Test
    void createFailsOnDuplicateCodeWithinClient() {
        UUID clientId = UUID.randomUUID();
        Client client = new Client();
        client.setId(clientId);
        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(repo.existsByClientIdAndCodeIgnoreCase(clientId, "CO-IN")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateCompanyRequest(
                                                UUID.randomUUID(),
                                                clientId,
                                                "CO-IN",
                                                "India",
                                                "IN")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void listUsedByCompanySwitcherFiltersToAccessibleClients() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Company co = new Company();
        co.setId(UUID.randomUUID());
        co.setTenantId(tenantId);
        Client client = new Client();
        client.setId(clientId);
        co.setClient(client);
        co.setName("India Pvt Ltd");

        when(guard.hasUnrestrictedAccess()).thenReturn(false);
        when(guard.accessibleClientIds()).thenReturn(Set.of(clientId));
        when(repo.findAllByClientIdInOrderByNameAsc(List.of(clientId))).thenReturn(List.of(co));

        List<CompanyResponse> result = service.list(tenantId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("India Pvt Ltd");
    }

    @Test
    void getByIdFailsWhenAbsent() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }
}
