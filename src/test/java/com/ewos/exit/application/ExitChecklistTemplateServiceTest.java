package com.ewos.exit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.exit.api.dto.ChecklistItemSpec;
import com.ewos.exit.api.dto.CreateExitChecklistTemplateRequest;
import com.ewos.exit.api.dto.ExitChecklistTemplateResponse;
import com.ewos.exit.domain.ClearanceDepartment;
import com.ewos.exit.domain.ExitChecklistItemTemplate;
import com.ewos.exit.domain.ExitChecklistTemplate;
import com.ewos.exit.infrastructure.persistence.ExitChecklistItemTemplateRepository;
import com.ewos.exit.infrastructure.persistence.ExitChecklistTemplateRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExitChecklistTemplateServiceTest {

    @Mock ExitChecklistTemplateRepository templates;
    @Mock ExitChecklistItemTemplateRepository items;
    @Mock ClientAccessGuard guard;

    private ExitChecklistTemplateService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ExitChecklistTemplateService(templates, items, guard);
    }

    @Test
    void createSavesTheTemplateAndItsItemsInOrder() {
        when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExitChecklistTemplateResponse resp =
                service.create(
                        tenantId,
                        new CreateExitChecklistTemplateRequest(
                                companyId,
                                null,
                                "Standard",
                                List.of(
                                        new ChecklistItemSpec(
                                                ClearanceDepartment.IT, "Laptop", null),
                                        new ChecklistItemSpec(
                                                ClearanceDepartment.ADMIN, "ID Card", null))));

        assertThat(resp.name()).isEqualTo("Standard");
        assertThat(resp.active()).isTrue();
        assertThat(resp.items()).hasSize(2);
        assertThat(resp.items().get(0).itemName()).isEqualTo("Laptop");
        assertThat(resp.items().get(0).sortOrder()).isZero();
        assertThat(resp.items().get(1).itemName()).isEqualTo("ID Card");
        assertThat(resp.items().get(1).sortOrder()).isEqualTo(1);
    }

    @Test
    void resolveEffectivePrefersTheOrgUnitSpecificTemplate() {
        UUID orgUnitId = UUID.randomUUID();
        ExitChecklistTemplate scoped = new ExitChecklistTemplate();
        scoped.setId(UUID.randomUUID());
        when(templates.findCandidates(tenantId, companyId, orgUnitId)).thenReturn(List.of(scoped));

        Optional<ExitChecklistTemplate> resolved =
                service.resolveEffective(tenantId, companyId, orgUnitId);

        assertThat(resolved).contains(scoped);
    }

    @Test
    void resolveEffectiveReturnsEmptyWhenNoTemplateIsConfigured() {
        UUID orgUnitId = UUID.randomUUID();
        when(templates.findCandidates(tenantId, companyId, orgUnitId)).thenReturn(List.of());

        assertThat(service.resolveEffective(tenantId, companyId, orgUnitId)).isEmpty();
    }

    @Test
    void itemsOfReturnsItemsInSortOrder() {
        UUID templateId = UUID.randomUUID();
        ExitChecklistItemTemplate first = new ExitChecklistItemTemplate();
        first.setSortOrder(0);
        ExitChecklistItemTemplate second = new ExitChecklistItemTemplate();
        second.setSortOrder(1);
        when(items.findAllByTenantIdAndTemplateIdOrderBySortOrderAsc(tenantId, templateId))
                .thenReturn(List.of(first, second));

        assertThat(service.itemsOf(tenantId, templateId)).containsExactly(first, second);
    }

    @Test
    void setActiveRejectsAnUnknownTemplate() {
        UUID id = UUID.randomUUID();
        when(templates.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(tenantId, id, false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void setActiveFlipsTheActiveFlag() {
        UUID id = UUID.randomUUID();
        ExitChecklistTemplate t = new ExitChecklistTemplate();
        t.setId(id);
        t.setCompanyId(companyId);
        t.setActive(true);
        when(templates.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(t));
        when(items.findAllByTenantIdAndTemplateIdOrderBySortOrderAsc(tenantId, id))
                .thenReturn(List.of());

        ExitChecklistTemplateResponse resp = service.setActive(tenantId, id, false);

        assertThat(resp.active()).isFalse();
    }
}
