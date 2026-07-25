package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CreateTenantRequest;
import com.ewos.tenancy.api.dto.TenantResponse;
import com.ewos.tenancy.api.dto.UpdateTenantRequest;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.domain.TenantStatus;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantRepository repo;

    private TenantService service;

    @BeforeEach
    void setUp() {
        service = new TenantService(repo, new TenancyMapper());
        org.mockito.Mockito.lenient()
                .when(repo.save(any(Tenant.class)))
                .thenAnswer(
                        inv -> {
                            Tenant t = inv.getArgument(0);
                            if (t.getId() == null) {
                                t.setId(UUID.randomUUID());
                            }
                            return t;
                        });
    }

    @Test
    void createSucceedsWhenCodeIsUnique() {
        when(repo.existsByCodeIgnoreCase("ACME")).thenReturn(false);

        TenantResponse r = service.create(new CreateTenantRequest("ACME", "Acme Corp"));

        assertThat(r.code()).isEqualTo("ACME");
        assertThat(r.name()).isEqualTo("Acme Corp");
        assertThat(r.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void createFailsOnDuplicateCode() {
        when(repo.existsByCodeIgnoreCase("ACME")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateTenantRequest("ACME", "Acme Corp")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void updateAppliesOnlyProvidedFields() {
        UUID id = UUID.randomUUID();
        Tenant existing = new Tenant();
        existing.setId(id);
        existing.setCode("ACME");
        existing.setName("Old name");
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        TenantResponse r =
                service.update(id, new UpdateTenantRequest("New name", TenantStatus.SUSPENDED));

        assertThat(r.name()).isEqualTo("New name");
        assertThat(r.status()).isEqualTo(TenantStatus.SUSPENDED);
    }

    @Test
    void getByIdFailsWhenAbsent() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deleteDelegatesToRepository() {
        UUID id = UUID.randomUUID();
        Tenant existing = new Tenant();
        existing.setId(id);
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        service.delete(id);

        org.mockito.Mockito.verify(repo).delete(existing);
    }
}
