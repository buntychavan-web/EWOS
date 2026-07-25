package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.ClientResponse;
import com.ewos.tenancy.api.dto.CreateClientRequest;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.ClientStatus;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.ClientRepository;
import com.ewos.tenancy.infrastructure.persistence.CompanyRepository;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
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
class ClientServiceTest {

    @Mock ClientRepository repo;
    @Mock TenantRepository tenantRepo;
    @Mock CompanyRepository companyRepo;
    @Mock ClientAccessGuard guard;

    private ClientService service;

    @BeforeEach
    void setUp() {
        service = new ClientService(repo, tenantRepo, companyRepo, guard, new TenancyMapper());
        org.mockito.Mockito.lenient()
                .when(repo.save(any(Client.class)))
                .thenAnswer(
                        inv -> {
                            Client c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
    }

    private Tenant tenant(UUID id) {
        Tenant t = new Tenant();
        t.setId(id);
        return t;
    }

    @Test
    void createSucceedsWhenCodeIsUnique() {
        UUID tenantId = UUID.randomUUID();
        when(repo.existsByTenantIdAndCodeIgnoreCase(tenantId, "CL-01")).thenReturn(false);
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.of(tenant(tenantId)));

        ClientResponse r =
                service.create(
                        new CreateClientRequest(
                                tenantId, "CL-01", "Northwind Traders", LocalDate.now()));

        assertThat(r.code()).isEqualTo("CL-01");
        assertThat(r.tenantId()).isEqualTo(tenantId);
        assertThat(r.status()).isEqualTo(ClientStatus.ACTIVE);
    }

    @Test
    void createFailsOnDuplicateCode() {
        UUID tenantId = UUID.randomUUID();
        when(repo.existsByTenantIdAndCodeIgnoreCase(tenantId, "CL-01")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateClientRequest(
                                                tenantId, "CL-01", "Northwind", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void getByIdEnforcesChineseWall() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Client existing = new Client();
        existing.setId(id);
        existing.setTenant(tenant(tenantId));
        when(repo.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized for this client"))
                .when(guard)
                .requireAccess(id);

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void listReturnsAllForUnrestrictedCaller() {
        UUID tenantId = UUID.randomUUID();
        Client c1 = new Client();
        c1.setId(UUID.randomUUID());
        c1.setTenant(tenant(tenantId));
        c1.setLegalName("A");
        when(guard.hasUnrestrictedAccess()).thenReturn(true);
        when(repo.findAllByTenantIdOrderByLegalNameAsc(tenantId)).thenReturn(List.of(c1));

        assertThat(service.list(tenantId)).hasSize(1);
    }

    @Test
    void listReturnsEmptyForRestrictedCallerWithNoAssignments() {
        UUID tenantId = UUID.randomUUID();
        when(guard.hasUnrestrictedAccess()).thenReturn(false);
        when(guard.accessibleClientIds()).thenReturn(java.util.Set.of());

        assertThat(service.list(tenantId)).isEmpty();
    }

    @Test
    void deleteRejectsWhenCompaniesStillReference() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Client existing = new Client();
        existing.setId(id);
        existing.setTenant(tenant(tenantId));
        when(repo.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));
        when(companyRepo.countByClientId(id)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(tenantId, id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("3 companies");
        verify(repo, org.mockito.Mockito.never()).delete(any());
    }
}
