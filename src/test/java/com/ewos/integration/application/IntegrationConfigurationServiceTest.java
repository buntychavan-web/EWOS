package com.ewos.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.integration.api.IntegrationMapper;
import com.ewos.integration.api.dto.CreateIntegrationConfigurationRequest;
import com.ewos.integration.api.dto.IntegrationConfigurationResponse;
import com.ewos.integration.api.dto.UpdateIntegrationConfigurationRequest;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationConfiguration;
import com.ewos.integration.infrastructure.persistence.IntegrationConfigurationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class IntegrationConfigurationServiceTest {

    @Mock IntegrationConfigurationRepository repository;
    @Mock ClientAccessGuard guard;

    private IntegrationConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new IntegrationConfigurationService(repository, guard, new IntegrationMapper());
        org.mockito.Mockito.lenient()
                .when(repository.save(any(IntegrationConfiguration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private static IntegrationConfiguration configuration(UUID companyId) {
        IntegrationConfiguration c = new IntegrationConfiguration();
        c.setTenantId(UUID.randomUUID());
        c.setCompanyId(companyId);
        c.setExchangeType("PAYROLL_RUN_EXPORT");
        c.setAdapterType(IntegrationAdapterType.CSV);
        c.setConfigJson("{\"outputDirectory\": \"/tmp\"}");
        c.setActive(true);
        return c;
    }

    @Test
    void createChecksAccessAndPersists() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.findByTenantIdAndCompanyIdAndExchangeTypeIgnoreCaseAndActiveTrue(
                        tenantId, companyId, "PAYROLL_RUN_EXPORT"))
                .thenReturn(Optional.empty());

        IntegrationConfigurationResponse r =
                service.create(
                        new CreateIntegrationConfigurationRequest(
                                tenantId,
                                companyId,
                                "PAYROLL_RUN_EXPORT",
                                IntegrationAdapterType.CSV,
                                "{\"outputDirectory\": \"/tmp\"}"));

        assertThat(r.active()).isTrue();
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createRejectsADuplicateActiveConfigurationForTheSameExchangeType() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.findByTenantIdAndCompanyIdAndExchangeTypeIgnoreCaseAndActiveTrue(
                        tenantId, companyId, "PAYROLL_RUN_EXPORT"))
                .thenReturn(Optional.of(configuration(companyId)));

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateIntegrationConfigurationRequest(
                                                tenantId,
                                                companyId,
                                                "PAYROLL_RUN_EXPORT",
                                                IntegrationAdapterType.CSV,
                                                "{}")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createDeniedWhenCallerLacksCompanyAccess() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateIntegrationConfigurationRequest(
                                                tenantId,
                                                companyId,
                                                "PAYROLL_RUN_EXPORT",
                                                IntegrationAdapterType.CSV,
                                                "{}")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void updateChangesConfigJsonAndActiveFlag() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        IntegrationConfiguration c = configuration(companyId);
        when(repository.findByIdAndTenantId(any(), any())).thenReturn(Optional.of(c));

        IntegrationConfigurationResponse updated =
                service.update(
                        tenantId,
                        UUID.randomUUID(),
                        new UpdateIntegrationConfigurationRequest(
                                "{\"outputDirectory\": \"/other\"}", false));

        assertThat(updated.configJson()).isEqualTo("{\"outputDirectory\": \"/other\"}");
        assertThat(updated.active()).isFalse();
    }

    @Test
    void getByIdThrowsWhenNotFound() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void deleteChecksAccessAndRemoves() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        IntegrationConfiguration c = configuration(companyId);
        when(repository.findByIdAndTenantId(any(), any())).thenReturn(Optional.of(c));

        service.delete(tenantId, UUID.randomUUID());

        verify(guard).requireAccessForCompany(companyId);
        verify(repository).delete(c);
    }
}
