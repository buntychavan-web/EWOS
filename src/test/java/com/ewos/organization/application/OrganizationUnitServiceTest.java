package com.ewos.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.organization.api.OrganizationMapper;
import com.ewos.organization.api.dto.CreateOrganizationUnitRequest;
import com.ewos.organization.api.dto.OrganizationUnitResponse;
import com.ewos.organization.api.dto.UpdateOrganizationUnitRequest;
import com.ewos.organization.domain.OrganizationHierarchyPolicy;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.domain.OrganizationUnitStatus;
import com.ewos.organization.domain.OrganizationUnitType;
import com.ewos.organization.domain.events.OrganizationUnitEvent;
import com.ewos.organization.domain.events.OrganizationUnitEventType;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitTypeRepository;
import com.ewos.shared.exception.ApiException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrganizationUnitServiceTest {

    @Mock OrganizationUnitRepository unitRepo;
    @Mock OrganizationUnitTypeRepository typeRepo;
    @Mock ApplicationEventPublisher events;

    private OrganizationUnitService service;
    private final OrganizationHierarchyPolicy policy = new OrganizationHierarchyPolicy();

    @BeforeEach
    void setUp() {
        service =
                new OrganizationUnitService(
                        unitRepo, typeRepo, policy, new OrganizationMapper(), events);
        org.mockito.Mockito.lenient()
                .when(unitRepo.save(any(OrganizationUnit.class)))
                .thenAnswer(
                        inv -> {
                            OrganizationUnit u = inv.getArgument(0);
                            if (u.getId() == null) {
                                u.setId(UUID.randomUUID());
                            }
                            return u;
                        });
    }

    @Test
    void createEmitsCreatedEventOnHappyPath() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        OrganizationUnitType type = typeStub(tenant);
        when(typeRepo.findByIdAndTenantId(type.getId(), tenant)).thenReturn(Optional.of(type));

        OrganizationUnitResponse r =
                service.create(
                        new CreateOrganizationUnitRequest(
                                tenant,
                                company,
                                type.getId(),
                                null,
                                "EMEA",
                                "EMEA Div",
                                "desc",
                                "de",
                                "CC-1",
                                UUID.randomUUID(),
                                LocalDate.of(2024, 1, 1),
                                null));

        assertThat(r.code()).isEqualTo("EMEA");
        assertThat(r.tenantId()).isEqualTo(tenant);
        assertThat(r.status()).isEqualTo(OrganizationUnitStatus.ACTIVE);
        assertThat(r.countryCode()).isEqualTo("DE"); // upper-cased

        ArgumentCaptor<OrganizationUnitEvent> ev =
                ArgumentCaptor.forClass(OrganizationUnitEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().eventType()).isEqualTo(OrganizationUnitEventType.CREATED);
        assertThat(ev.getValue().unitId()).isEqualTo(r.id());
    }

    @Test
    void createRejectsDuplicateCode() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        when(unitRepo.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenant, company, "DUP"))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateOrganizationUnitRequest(
                                                tenant,
                                                company,
                                                UUID.randomUUID(),
                                                null,
                                                "DUP",
                                                "Duplicate",
                                                null,
                                                null,
                                                null,
                                                null,
                                                LocalDate.now(),
                                                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void updateReparentsAndEmitsReparentedEvent() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        OrganizationUnit existing = unit(tenant, company, "CHILD", null);
        OrganizationUnit newParent = unit(tenant, company, "NEW-PARENT", null);
        when(unitRepo.findByIdAndTenantId(existing.getId(), tenant))
                .thenReturn(Optional.of(existing));
        when(unitRepo.findByIdAndTenantId(newParent.getId(), tenant))
                .thenReturn(Optional.of(newParent));

        service.update(
                tenant,
                existing.getId(),
                new UpdateOrganizationUnitRequest(
                        null, newParent.getId(), null, null, null, null, null, null));

        ArgumentCaptor<OrganizationUnitEvent> ev =
                ArgumentCaptor.forClass(OrganizationUnitEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().eventType()).isEqualTo(OrganizationUnitEventType.REPARENTED);
        assertThat(existing.getParent()).isSameAs(newParent);
    }

    @Test
    void closeSetsEffectiveToAndEmitsClosedEvent() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        OrganizationUnit u = unit(tenant, company, "LEAF", null);
        when(unitRepo.findByIdAndTenantId(u.getId(), tenant)).thenReturn(Optional.of(u));
        when(unitRepo.countChildren(u.getId())).thenReturn(0L);

        service.changeStatus(tenant, u.getId(), OrganizationUnitStatus.CLOSED);

        assertThat(u.getStatus()).isEqualTo(OrganizationUnitStatus.CLOSED);
        assertThat(u.getEffectiveTo()).isNotNull();
        ArgumentCaptor<OrganizationUnitEvent> ev =
                ArgumentCaptor.forClass(OrganizationUnitEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().eventType()).isEqualTo(OrganizationUnitEventType.CLOSED);
    }

    @Test
    void closeRejectsWhenChildrenExist() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        OrganizationUnit u = unit(tenant, company, "PARENT", null);
        when(unitRepo.findByIdAndTenantId(u.getId(), tenant)).thenReturn(Optional.of(u));
        when(unitRepo.countChildren(u.getId())).thenReturn(2L);

        assertThatThrownBy(
                        () ->
                                service.changeStatus(
                                        tenant, u.getId(), OrganizationUnitStatus.CLOSED))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("2 active children");
    }

    @Test
    void deleteRejectsWhenChildrenExist() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        OrganizationUnit u = unit(tenant, company, "PARENT", null);
        when(unitRepo.findByIdAndTenantId(u.getId(), tenant)).thenReturn(Optional.of(u));
        when(unitRepo.countChildren(u.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(tenant, u.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("1 children");
    }

    private OrganizationUnitType typeStub(UUID tenant) {
        OrganizationUnitType t = new OrganizationUnitType();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenant);
        t.setCode("DEPT");
        t.setName("Department");
        t.setActive(true);
        return t;
    }

    private OrganizationUnit unit(UUID tenant, UUID company, String code, OrganizationUnit parent) {
        OrganizationUnit u = new OrganizationUnit();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenant);
        u.setCompanyId(company);
        u.setUnitType(typeStub(tenant));
        u.setParent(parent);
        u.setCode(code);
        u.setName(code);
        u.setEffectiveFrom(LocalDate.now());
        u.setStatus(OrganizationUnitStatus.ACTIVE);
        return u;
    }
}
