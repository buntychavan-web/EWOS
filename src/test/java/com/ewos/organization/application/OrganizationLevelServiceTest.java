package com.ewos.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.company.infrastructure.persistence.TenantRepository;
import com.ewos.organization.api.dto.CreateOrganizationLevelRequest;
import com.ewos.organization.api.dto.OrganizationLevelResponse;
import com.ewos.organization.domain.OrganizationLevel;
import com.ewos.organization.infrastructure.persistence.OrganizationLevelRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationNodeRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrganizationLevelServiceTest {

    @Mock OrganizationLevelRepository levelRepository;
    @Mock OrganizationNodeRepository nodeRepository;
    @Mock TenantRepository tenantRepository;

    private OrganizationLevelService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setCode("DEFAULT");
        tenant.setName("Default Tenant");
        lenient().when(tenantRepository.findByCode("DEFAULT")).thenReturn(Optional.of(tenant));
        lenient().when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        lenient()
                .when(levelRepository.save(any(OrganizationLevel.class)))
                .thenAnswer(
                        inv -> {
                            OrganizationLevel l = inv.getArgument(0);
                            if (l.getId() == null) {
                                l.setId(UUID.randomUUID());
                            }
                            return l;
                        });

        service =
                new OrganizationLevelService(
                        levelRepository, nodeRepository, new TenantResolver(tenantRepository));
    }

    @Test
    void createLevelPersistsAndReturnsResponse() {
        CreateOrganizationLevelRequest req =
                new CreateOrganizationLevelRequest(
                        null, "BU", "Business Unit", 1, null, LocalDate.of(2026, 1, 1));
        when(levelRepository.findByTenantAndCode(tenant, "BU")).thenReturn(Optional.empty());

        OrganizationLevelResponse resp = service.create(req);

        assertThat(resp.code()).isEqualTo("BU");
        assertThat(resp.displaySequence()).isEqualTo(1);
        assertThat(resp.active()).isTrue();
        verify(levelRepository).save(any(OrganizationLevel.class));
    }

    @Test
    void createLevelRejectsDuplicateCodeInTenant() {
        OrganizationLevel existing = new OrganizationLevel();
        existing.setCode("BU");
        existing.setTenant(tenant);
        when(levelRepository.findByTenantAndCode(tenant, "BU")).thenReturn(Optional.of(existing));

        CreateOrganizationLevelRequest req =
                new CreateOrganizationLevelRequest(
                        null, "BU", "Business Unit", 1, null, LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void softDeleteRejectsLevelWithChildLevels() {
        OrganizationLevel level = level("BU");
        when(levelRepository.findById(level.getId())).thenReturn(Optional.of(level));
        when(levelRepository.existsByParentLevel(level)).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(level.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void softDeleteRejectsLevelInUseByNodes() {
        OrganizationLevel level = level("BU");
        when(levelRepository.findById(level.getId())).thenReturn(Optional.of(level));
        when(levelRepository.existsByParentLevel(level)).thenReturn(false);
        when(nodeRepository.existsByLevel(level)).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(level.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private OrganizationLevel level(String code) {
        OrganizationLevel l = new OrganizationLevel();
        l.setId(UUID.randomUUID());
        l.setTenant(tenant);
        l.setCode(code);
        l.setName(code);
        l.setDisplaySequence(1);
        l.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        l.setActive(true);
        return l;
    }
}
