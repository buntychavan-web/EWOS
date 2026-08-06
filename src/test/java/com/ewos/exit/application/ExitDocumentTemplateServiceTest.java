package com.ewos.exit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.exit.api.dto.CreateExitDocumentTemplateRequest;
import com.ewos.exit.api.dto.ExitDocumentTemplateResponse;
import com.ewos.exit.domain.ExitDocumentTemplate;
import com.ewos.exit.domain.ExitDocumentType;
import com.ewos.exit.infrastructure.persistence.ExitDocumentTemplateRepository;
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
class ExitDocumentTemplateServiceTest {

    @Mock ExitDocumentTemplateRepository templates;
    @Mock ClientAccessGuard guard;

    private ExitDocumentTemplateService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ExitDocumentTemplateService(templates, guard);
    }

    @Test
    void createSavesTheTemplate() {
        when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExitDocumentTemplateResponse resp =
                service.create(
                        tenantId,
                        new CreateExitDocumentTemplateRequest(
                                companyId,
                                null,
                                ExitDocumentType.RELIEVING_LETTER,
                                "Relieving Letter",
                                "Dear {{employeeName}}, your last working day is"
                                        + " {{lastWorkingDate}}."));

        assertThat(resp.documentType()).isEqualTo(ExitDocumentType.RELIEVING_LETTER);
        assertThat(resp.title()).isEqualTo("Relieving Letter");
        assertThat(resp.active()).isTrue();
    }

    @Test
    void resolveEffectivePrefersTheOrgUnitSpecificTemplate() {
        UUID orgUnitId = UUID.randomUUID();
        ExitDocumentTemplate scoped = new ExitDocumentTemplate();
        scoped.setId(UUID.randomUUID());
        when(templates.findCandidates(
                        tenantId, companyId, orgUnitId, ExitDocumentType.RELIEVING_LETTER))
                .thenReturn(List.of(scoped));

        assertThat(
                        service.resolveEffective(
                                tenantId, companyId, orgUnitId, ExitDocumentType.RELIEVING_LETTER))
                .contains(scoped);
    }

    @Test
    void resolveEffectiveReturnsEmptyWhenNoneIsConfigured() {
        when(templates.findCandidates(
                        tenantId, companyId, null, ExitDocumentType.SERVICE_CERTIFICATE))
                .thenReturn(List.of());

        assertThat(
                        service.resolveEffective(
                                tenantId, companyId, null, ExitDocumentType.SERVICE_CERTIFICATE))
                .isEmpty();
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
        ExitDocumentTemplate t = new ExitDocumentTemplate();
        t.setId(id);
        t.setCompanyId(companyId);
        t.setActive(true);
        when(templates.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(t));

        assertThat(service.setActive(tenantId, id, false).active()).isFalse();
    }
}
