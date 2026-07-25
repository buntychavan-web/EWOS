package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.ClientAssignmentResponse;
import com.ewos.tenancy.api.dto.CreateClientAssignmentRequest;
import com.ewos.tenancy.api.dto.UpdateClientAssignmentRequest;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.ClientAssignment;
import com.ewos.tenancy.domain.ClientAssignmentScopeRole;
import com.ewos.tenancy.domain.PayrollServiceProvider;
import com.ewos.tenancy.domain.ServiceOffering;
import com.ewos.tenancy.infrastructure.persistence.ClientAssignmentRepository;
import com.ewos.tenancy.infrastructure.persistence.ClientRepository;
import com.ewos.tenancy.infrastructure.persistence.PayrollServiceProviderRepository;
import com.ewos.tenancy.infrastructure.persistence.ServiceOfferingRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientAssignmentServiceTest {

    @Mock ClientAssignmentRepository repo;
    @Mock PayrollServiceProviderRepository providerRepo;
    @Mock ClientRepository clientRepo;
    @Mock ServiceOfferingRepository serviceRepo;

    private ClientAssignmentService service;

    @BeforeEach
    void setUp() {
        service =
                new ClientAssignmentService(
                        repo, providerRepo, clientRepo, serviceRepo, new TenancyMapper());
        org.mockito.Mockito.lenient()
                .when(repo.save(any(ClientAssignment.class)))
                .thenAnswer(
                        inv -> {
                            ClientAssignment a = inv.getArgument(0);
                            if (a.getId() == null) {
                                a.setId(UUID.randomUUID());
                            }
                            return a;
                        });
    }

    @Test
    void createWithNullServiceMeansFullClientAccess() {
        UUID providerId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PayrollServiceProvider provider = new PayrollServiceProvider();
        provider.setId(providerId);
        Client client = new Client();
        client.setId(clientId);
        when(providerRepo.findById(providerId)).thenReturn(Optional.of(provider));
        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));

        ClientAssignmentResponse r =
                service.create(
                        new CreateClientAssignmentRequest(
                                providerId,
                                userId,
                                clientId,
                                null,
                                ClientAssignmentScopeRole.PAYROLL_ADMIN,
                                LocalDate.now(),
                                null));

        assertThat(r.serviceId()).isNull();
        assertThat(r.userId()).isEqualTo(userId);
        assertThat(r.scopeRole()).isEqualTo(ClientAssignmentScopeRole.PAYROLL_ADMIN);
    }

    @Test
    void createWithServiceNarrowsTheGrant() {
        UUID providerId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        PayrollServiceProvider provider = new PayrollServiceProvider();
        provider.setId(providerId);
        Client client = new Client();
        client.setId(clientId);
        ServiceOffering svc = new ServiceOffering();
        svc.setId(serviceId);
        when(providerRepo.findById(providerId)).thenReturn(Optional.of(provider));
        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(serviceRepo.findById(serviceId)).thenReturn(Optional.of(svc));

        ClientAssignmentResponse r =
                service.create(
                        new CreateClientAssignmentRequest(
                                providerId,
                                UUID.randomUUID(),
                                clientId,
                                serviceId,
                                ClientAssignmentScopeRole.PAYROLL_PROCESSOR,
                                LocalDate.now(),
                                null));

        assertThat(r.serviceId()).isEqualTo(serviceId);
    }

    @Test
    void createFailsWhenServiceIdUnknown() {
        UUID providerId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        when(providerRepo.findById(providerId))
                .thenReturn(Optional.of(new PayrollServiceProvider()));
        when(clientRepo.findById(clientId)).thenReturn(Optional.of(new Client()));
        when(serviceRepo.findById(serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateClientAssignmentRequest(
                                                providerId,
                                                UUID.randomUUID(),
                                                clientId,
                                                serviceId,
                                                ClientAssignmentScopeRole.AUDITOR_READ_ONLY,
                                                LocalDate.now(),
                                                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Service offering not found");
    }

    @Test
    void revokeIsImmediate() {
        UUID id = UUID.randomUUID();
        ClientAssignment existing = new ClientAssignment();
        existing.setId(id);
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        service.revoke(id);

        org.mockito.Mockito.verify(repo).delete(existing);
    }

    @Test
    void updateChangesScopeAndActiveFlag() {
        UUID id = UUID.randomUUID();
        ClientAssignment existing = new ClientAssignment();
        existing.setId(id);
        existing.setProvider(new PayrollServiceProvider());
        existing.setClient(new Client());
        existing.setScopeRole(ClientAssignmentScopeRole.AUDITOR_READ_ONLY);
        existing.setActive(true);
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        ClientAssignmentResponse r =
                service.update(
                        id,
                        new UpdateClientAssignmentRequest(
                                ClientAssignmentScopeRole.PAYROLL_REVIEWER, false, null));

        assertThat(r.scopeRole()).isEqualTo(ClientAssignmentScopeRole.PAYROLL_REVIEWER);
        assertThat(r.active()).isFalse();
    }
}
