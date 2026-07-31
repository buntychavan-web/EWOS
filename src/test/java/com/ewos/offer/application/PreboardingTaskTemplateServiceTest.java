package com.ewos.offer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.offer.api.OfferMapper;
import com.ewos.offer.api.dto.CreatePreboardingTaskTemplateRequest;
import com.ewos.offer.domain.preboarding.PreboardingTaskOwner;
import com.ewos.offer.domain.preboarding.PreboardingTaskTemplate;
import com.ewos.offer.domain.preboarding.PreboardingTaskType;
import com.ewos.offer.infrastructure.persistence.PreboardingTaskTemplateRepository;
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
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PreboardingTaskTemplateServiceTest {

    @Mock PreboardingTaskTemplateRepository templates;
    @Mock ClientAccessGuard guard;

    private final OfferMapper mapper = new OfferMapper();

    private PreboardingTaskTemplateService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PreboardingTaskTemplateService(templates, mapper, guard);
    }

    private CreatePreboardingTaskTemplateRequest createRequest() {
        return new CreatePreboardingTaskTemplateRequest(
                tenantId,
                companyId,
                "BGV-1",
                "Background Verification",
                null,
                PreboardingTaskType.BACKGROUND_VERIFICATION,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void createRejectsDuplicateCode() {
        when(templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "BGV-1"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(templates, never()).save(any());
    }

    @Test
    void createDefaultsMandatoryTrueAndOwnerHr() {
        when(templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "BGV-1"))
                .thenReturn(false);
        when(templates.save(any(PreboardingTaskTemplate.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var resp = service.create(createRequest());

        assertThat(resp.mandatory()).isTrue();
        assertThat(resp.defaultOwner()).isEqualTo(PreboardingTaskOwner.HR);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(templates.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteChecksCompanyAccess() {
        PreboardingTaskTemplate t = new PreboardingTaskTemplate();
        t.setId(UUID.randomUUID());
        t.setCompanyId(companyId);
        when(templates.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));

        service.delete(tenantId, t.getId());

        verify(guard).requireAccessForCompany(companyId);
        verify(templates).delete(t);
    }

    @Test
    void activeTemplatesForDelegatesToRepository() {
        when(templates.findAllByTenantIdAndCompanyIdAndActiveTrueOrderBySortOrderAsc(
                        tenantId, companyId))
                .thenReturn(List.of());

        var result = service.activeTemplatesFor(tenantId, companyId);

        assertThat(result).isEmpty();
    }
}
