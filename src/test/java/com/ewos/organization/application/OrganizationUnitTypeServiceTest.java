package com.ewos.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.organization.api.OrganizationMapper;
import com.ewos.organization.api.dto.CreateOrganizationUnitTypeRequest;
import com.ewos.organization.api.dto.OrganizationUnitTypeResponse;
import com.ewos.organization.api.dto.UpdateOrganizationUnitTypeRequest;
import com.ewos.organization.domain.OrganizationUnitType;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitTypeRepository;
import com.ewos.shared.exception.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationUnitTypeServiceTest {

    @Mock OrganizationUnitTypeRepository repo;

    private OrganizationUnitTypeService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationUnitTypeService(repo, new OrganizationMapper());
        org.mockito.Mockito.lenient()
                .when(repo.save(any(OrganizationUnitType.class)))
                .thenAnswer(
                        inv -> {
                            OrganizationUnitType t = inv.getArgument(0);
                            if (t.getId() == null) {
                                t.setId(UUID.randomUUID());
                            }
                            return t;
                        });
    }

    @Test
    void createSucceedsWhenCodeIsUnique() {
        UUID tenant = UUID.randomUUID();
        when(repo.existsByTenantIdAndCodeIgnoreCase(tenant, "DEPT")).thenReturn(false);

        OrganizationUnitTypeResponse r =
                service.create(
                        new CreateOrganizationUnitTypeRequest(
                                tenant, "DEPT", "Department", null, 10, true));

        assertThat(r.code()).isEqualTo("DEPT");
        assertThat(r.tenantId()).isEqualTo(tenant);
        assertThat(r.sortOrder()).isEqualTo(10);
        assertThat(r.active()).isTrue();
    }

    @Test
    void createFailsOnDuplicateCode() {
        UUID tenant = UUID.randomUUID();
        when(repo.existsByTenantIdAndCodeIgnoreCase(tenant, "DEPT")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateOrganizationUnitTypeRequest(
                                                tenant, "DEPT", "Department", null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void updateAppliesOnlyProvidedFields() {
        UUID tenant = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        OrganizationUnitType existing = new OrganizationUnitType();
        existing.setId(id);
        existing.setTenantId(tenant);
        existing.setCode("DEPT");
        existing.setName("Old");
        existing.setSortOrder(100);
        existing.setActive(true);
        when(repo.findByIdAndTenantId(id, tenant)).thenReturn(Optional.of(existing));

        OrganizationUnitTypeResponse r =
                service.update(
                        tenant,
                        id,
                        new UpdateOrganizationUnitTypeRequest("New name", null, 5, false));

        assertThat(r.name()).isEqualTo("New name");
        assertThat(r.sortOrder()).isEqualTo(5);
        assertThat(r.active()).isFalse();
    }

    @Test
    void getByIdFailsWhenAbsent() {
        UUID tenant = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(tenant, id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }
}
