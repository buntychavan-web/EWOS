package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CreateServiceOfferingRequest;
import com.ewos.tenancy.api.dto.ServiceOfferingResponse;
import com.ewos.tenancy.api.dto.UpdateServiceOfferingRequest;
import com.ewos.tenancy.domain.ServiceOffering;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.ServiceOfferingRepository;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceOfferingServiceTest {

    @Mock ServiceOfferingRepository repo;
    @Mock TenantRepository tenantRepo;

    private ServiceOfferingService service;

    @BeforeEach
    void setUp() {
        service = new ServiceOfferingService(repo, tenantRepo, new TenancyMapper());
        org.mockito.Mockito.lenient()
                .when(repo.save(any(ServiceOffering.class)))
                .thenAnswer(
                        inv -> {
                            ServiceOffering s = inv.getArgument(0);
                            if (s.getId() == null) {
                                s.setId(UUID.randomUUID());
                            }
                            return s;
                        });
    }

    @Test
    void createSeedsACatalogueEntry() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        when(repo.existsByTenantIdAndCodeIgnoreCase(tenantId, "PAYROLL_PROCESSING"))
                .thenReturn(false);
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.of(tenant));

        ServiceOfferingResponse r =
                service.create(
                        new CreateServiceOfferingRequest(
                                tenantId,
                                "PAYROLL_PROCESSING",
                                "Payroll Processing",
                                "End-to-end payroll run",
                                "PAYROLL",
                                10,
                                true));

        assertThat(r.code()).isEqualTo("PAYROLL_PROCESSING");
        assertThat(r.category()).isEqualTo("PAYROLL");
        assertThat(r.active()).isTrue();
    }

    @Test
    void createFailsOnDuplicateCode() {
        UUID tenantId = UUID.randomUUID();
        when(repo.existsByTenantIdAndCodeIgnoreCase(tenantId, "PAYROLL_PROCESSING"))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateServiceOfferingRequest(
                                                tenantId,
                                                "PAYROLL_PROCESSING",
                                                "x",
                                                null,
                                                null,
                                                null,
                                                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void updateAppliesOnlyProvidedFields() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ServiceOffering existing = new ServiceOffering();
        existing.setId(id);
        existing.setTenant(new Tenant());
        existing.setCode("HR_HELPDESK");
        existing.setName("HR Helpdesk");
        existing.setSortOrder(100);
        existing.setActive(true);
        when(repo.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        ServiceOfferingResponse r =
                service.update(
                        tenantId, id, new UpdateServiceOfferingRequest(null, null, "HR", 5, false));

        assertThat(r.name()).isEqualTo("HR Helpdesk");
        assertThat(r.category()).isEqualTo("HR");
        assertThat(r.sortOrder()).isEqualTo(5);
        assertThat(r.active()).isFalse();
    }
}
