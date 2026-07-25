package com.ewos.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.integration.api.IntegrationMapper;
import com.ewos.integration.api.dto.ClientGoLiveConfigurationResponse;
import com.ewos.integration.api.dto.CreateClientGoLiveConfigurationRequest;
import com.ewos.integration.api.dto.UpdateClientGoLiveConfigurationRequest;
import com.ewos.integration.domain.ClientGoLiveConfiguration;
import com.ewos.integration.domain.ClientGoLiveStatus;
import com.ewos.integration.infrastructure.persistence.ClientGoLiveConfigurationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ClientGoLiveConfigurationServiceTest {

    @Mock ClientGoLiveConfigurationRepository repository;
    @Mock ClientAccessGuard guard;

    private ClientGoLiveConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new ClientGoLiveConfigurationService(repository, guard, new IntegrationMapper());
        org.mockito.Mockito.lenient()
                .when(repository.save(any(ClientGoLiveConfiguration.class)))
                .thenAnswer(
                        inv -> {
                            ClientGoLiveConfiguration c = inv.getArgument(0);
                            return c;
                        });
    }

    private static ClientGoLiveConfiguration configuration(UUID companyId, ClientGoLiveStatus status) {
        ClientGoLiveConfiguration c = new ClientGoLiveConfiguration();
        c.setTenantId(UUID.randomUUID());
        c.setClientId(UUID.randomUUID());
        c.setCompanyId(companyId);
        c.setStatus(status);
        return c;
    }

    @Test
    void createChecksAccessAndStartsAtPlanning() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        ClientGoLiveConfigurationResponse r =
                service.create(
                        new CreateClientGoLiveConfigurationRequest(
                                tenantId, clientId, companyId, LocalDate.of(2026, 9, 1), "notes"));

        assertThat(r.status()).isEqualTo(ClientGoLiveStatus.PLANNING);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createRejectsADuplicateConfigurationForTheSameCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.existsByCompanyId(companyId)).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateClientGoLiveConfigurationRequest(
                                                tenantId, clientId, companyId, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createDeniedWhenCallerLacksCompanyAccess() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateClientGoLiveConfigurationRequest(
                                                tenantId, clientId, companyId, null, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void updateChangesStatusAndDate() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        ClientGoLiveConfiguration c = configuration(companyId, ClientGoLiveStatus.PLANNING);
        when(repository.findByIdAndTenantId(any(), any())).thenReturn(Optional.of(c));

        ClientGoLiveConfigurationResponse updated =
                service.update(
                        tenantId,
                        UUID.randomUUID(),
                        new UpdateClientGoLiveConfigurationRequest(
                                LocalDate.of(2026, 10, 1), ClientGoLiveStatus.READY, null));

        assertThat(updated.status()).isEqualTo(ClientGoLiveStatus.READY);
        assertThat(updated.goLiveDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void forCompanyThrowsWhenNoneExists() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.findByTenantIdAndCompanyId(tenantId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forCompany(tenantId, companyId))
                .isInstanceOf(ApiException.class);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void forClientChecksClientLevelAccess() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        when(repository.findAllByTenantIdAndClientIdOrderByCreatedAtDesc(tenantId, clientId))
                .thenReturn(List.of(configuration(UUID.randomUUID(), ClientGoLiveStatus.LIVE)));

        List<ClientGoLiveConfigurationResponse> results = service.forClient(tenantId, clientId);

        assertThat(results).hasSize(1);
        verify(guard).requireAccess(clientId);
    }
}
