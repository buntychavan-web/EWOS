package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CreatePayrollCollaborationRequest;
import com.ewos.tenancy.api.dto.PayrollCollaborationResponse;
import com.ewos.tenancy.api.dto.UpdatePayrollCollaborationRequest;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.PayrollCollaboration;
import com.ewos.tenancy.domain.PayrollCollaborationScope;
import com.ewos.tenancy.domain.PayrollCollaborationStatus;
import com.ewos.tenancy.domain.PayrollServiceProvider;
import com.ewos.tenancy.infrastructure.persistence.ClientRepository;
import com.ewos.tenancy.infrastructure.persistence.PayrollCollaborationRepository;
import com.ewos.tenancy.infrastructure.persistence.PayrollServiceProviderRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayrollCollaborationServiceTest {

    @Mock PayrollCollaborationRepository repo;
    @Mock ClientRepository clientRepo;
    @Mock PayrollServiceProviderRepository providerRepo;
    @Mock ClientAccessGuard guard;

    private PayrollCollaborationService service;

    @BeforeEach
    void setUp() {
        service =
                new PayrollCollaborationService(
                        repo, clientRepo, providerRepo, guard, new TenancyMapper());
        org.mockito.Mockito.lenient()
                .when(repo.save(any(PayrollCollaboration.class)))
                .thenAnswer(
                        inv -> {
                            PayrollCollaboration c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
    }

    @Test
    void createEstablishesAnActiveCollaboration() {
        UUID clientId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        Client client = new Client();
        client.setId(clientId);
        PayrollServiceProvider provider = new PayrollServiceProvider();
        provider.setId(providerId);
        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(providerRepo.findById(providerId)).thenReturn(Optional.of(provider));

        PayrollCollaborationResponse r =
                service.create(
                        new CreatePayrollCollaborationRequest(
                                clientId,
                                providerId,
                                PayrollCollaborationScope.FULL,
                                LocalDate.now(),
                                null,
                                5));

        assertThat(r.clientId()).isEqualTo(clientId);
        assertThat(r.providerId()).isEqualTo(providerId);
        assertThat(r.scope()).isEqualTo(PayrollCollaborationScope.FULL);
        verify(guard).requireAccess(clientId);
    }

    @Test
    void createFailsWhenClientUnknown() {
        UUID clientId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        when(clientRepo.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreatePayrollCollaborationRequest(
                                                clientId,
                                                providerId,
                                                PayrollCollaborationScope.FULL,
                                                LocalDate.now(),
                                                null,
                                                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Client not found");
    }

    @Test
    void createDeniedWhenCallerLacksClientAccess() {
        UUID clientId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        Client client = new Client();
        client.setId(clientId);
        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        doThrow(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccess(clientId);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreatePayrollCollaborationRequest(
                                                clientId,
                                                providerId,
                                                PayrollCollaborationScope.FULL,
                                                LocalDate.now(),
                                                null,
                                                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void createRejectsDuplicateActiveCollaboration() {
        UUID clientId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        Client client = new Client();
        client.setId(clientId);
        PayrollServiceProvider provider = new PayrollServiceProvider();
        provider.setId(providerId);
        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(providerRepo.findById(providerId)).thenReturn(Optional.of(provider));
        when(repo.existsByClientIdAndProviderIdAndStatus(
                        clientId, providerId, PayrollCollaborationStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreatePayrollCollaborationRequest(
                                                clientId,
                                                providerId,
                                                PayrollCollaborationScope.FULL,
                                                LocalDate.now(),
                                                null,
                                                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateChangesScopeStatusAndSla() {
        UUID id = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        PayrollCollaboration existing = new PayrollCollaboration();
        existing.setId(id);
        Client client = new Client();
        client.setId(clientId);
        existing.setClient(client);
        existing.setProvider(new PayrollServiceProvider());
        existing.setScope(PayrollCollaborationScope.FULL);
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        PayrollCollaborationResponse r =
                service.update(
                        id,
                        new UpdatePayrollCollaborationRequest(
                                PayrollCollaborationScope.STATUTORY_ONLY,
                                PayrollCollaborationStatus.SUSPENDED,
                                null,
                                10));

        assertThat(r.scope()).isEqualTo(PayrollCollaborationScope.STATUTORY_ONLY);
        assertThat(r.status()).isEqualTo(PayrollCollaborationStatus.SUSPENDED);
        assertThat(r.slaDays()).isEqualTo(10);
        verify(guard).requireAccess(clientId);
    }

    @Test
    void getByIdEnforcesClientAccess() {
        UUID id = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        PayrollCollaboration existing = new PayrollCollaboration();
        existing.setId(id);
        Client client = new Client();
        client.setId(clientId);
        existing.setClient(client);
        existing.setProvider(new PayrollServiceProvider());
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        doThrow(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccess(clientId);

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ApiException.class);
    }

    @Test
    void deleteEnforcesClientAccessThenRemoves() {
        UUID id = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        PayrollCollaboration existing = new PayrollCollaboration();
        existing.setId(id);
        Client client = new Client();
        client.setId(clientId);
        existing.setClient(client);
        existing.setProvider(new PayrollServiceProvider());
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        service.delete(id);

        verify(guard).requireAccess(clientId);
        verify(repo).delete(existing);
    }

    @Test
    void listByClientDelegatesToGuardAndRepository() {
        UUID clientId = UUID.randomUUID();
        PayrollCollaboration c = new PayrollCollaboration();
        c.setId(UUID.randomUUID());
        Client client = new Client();
        client.setId(clientId);
        c.setClient(client);
        c.setProvider(new PayrollServiceProvider());
        when(repo.findAllByClientIdOrderByCreatedAtDesc(clientId)).thenReturn(List.of(c));

        List<PayrollCollaborationResponse> results = service.listByClient(clientId);

        assertThat(results).hasSize(1);
        verify(guard).requireAccess(clientId);
    }

    @Test
    void listByProviderIsNotClientScoped() {
        UUID providerId = UUID.randomUUID();
        PayrollCollaboration c = new PayrollCollaboration();
        c.setId(UUID.randomUUID());
        c.setClient(new Client());
        PayrollServiceProvider provider = new PayrollServiceProvider();
        provider.setId(providerId);
        c.setProvider(provider);
        when(repo.findAllByProviderIdOrderByCreatedAtDesc(providerId)).thenReturn(List.of(c));

        List<PayrollCollaborationResponse> results = service.listByProvider(providerId);

        assertThat(results).hasSize(1);
        org.mockito.Mockito.verifyNoInteractions(guard);
    }
}
