package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CreatePayrollServiceProviderRequest;
import com.ewos.tenancy.api.dto.PayrollServiceProviderResponse;
import com.ewos.tenancy.domain.PayrollServiceProvider;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.PayrollServiceProviderRepository;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayrollServiceProviderServiceTest {

    @Mock PayrollServiceProviderRepository repo;
    @Mock TenantRepository tenantRepo;

    private PayrollServiceProviderService service;

    @BeforeEach
    void setUp() {
        service = new PayrollServiceProviderService(repo, tenantRepo, new TenancyMapper());
        org.mockito.Mockito.lenient()
                .when(repo.save(any(PayrollServiceProvider.class)))
                .thenAnswer(
                        inv -> {
                            PayrollServiceProvider p = inv.getArgument(0);
                            if (p.getId() == null) {
                                p.setId(UUID.randomUUID());
                            }
                            return p;
                        });
    }

    @Test
    void createSucceedsWhenCodeIsUnique() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        when(repo.existsByTenantIdAndCodeIgnoreCase(tenantId, "PROV-01")).thenReturn(false);
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.of(tenant));

        PayrollServiceProviderResponse r =
                service.create(
                        new CreatePayrollServiceProviderRequest(
                                tenantId, "PROV-01", "Acme Payroll Services"));

        assertThat(r.code()).isEqualTo("PROV-01");
        assertThat(r.name()).isEqualTo("Acme Payroll Services");
    }

    @Test
    void createFailsWhenTenantMissing() {
        UUID tenantId = UUID.randomUUID();
        when(repo.existsByTenantIdAndCodeIgnoreCase(tenantId, "PROV-01")).thenReturn(false);
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreatePayrollServiceProviderRequest(
                                                tenantId, "PROV-01", "Acme")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tenant not found");
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
