package com.ewos.onboarding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.onboarding.api.OnboardingMapper;
import com.ewos.onboarding.api.dto.CreateOnboardingTaskTemplateRequest;
import com.ewos.onboarding.domain.OnboardingTaskOwner;
import com.ewos.onboarding.domain.OnboardingTaskTemplate;
import com.ewos.onboarding.domain.OnboardingTaskType;
import com.ewos.onboarding.infrastructure.persistence.OnboardingTaskTemplateRepository;
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

/**
 * Onboarding task template catalogue: per-company code uniqueness, and the default
 * mandatory/owner/active values {@link OnboardingPlanService} relies on when materialising tasks.
 */
@ExtendWith(MockitoExtension.class)
class OnboardingTaskTemplateServiceTest {

    @Mock OnboardingTaskTemplateRepository templates;
    @Mock ClientAccessGuard guard;

    private OnboardingTaskTemplateService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OnboardingTaskTemplateService(templates, new OnboardingMapper(), guard);
        org.mockito.Mockito.lenient()
                .when(templates.save(any(OnboardingTaskTemplate.class)))
                .thenAnswer(
                        inv -> {
                            OnboardingTaskTemplate t = inv.getArgument(0);
                            if (t.getId() == null) {
                                t.setId(UUID.randomUUID());
                            }
                            return t;
                        });
    }

    private CreateOnboardingTaskTemplateRequest request(String code, Boolean mandatory) {
        return new CreateOnboardingTaskTemplateRequest(
                tenantId,
                companyId,
                code,
                "Upload ID proof",
                null,
                OnboardingTaskType.DOCUMENT_UPLOAD,
                null,
                mandatory,
                null,
                null,
                null);
    }

    @Test
    void createRejectsADuplicateCodeForTheSameCompany() {
        when(templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                        tenantId, companyId, "ID_UPLOAD"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request("ID_UPLOAD", true)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createDefaultsMandatoryOwnerAndActiveWhenOmitted() {
        when(templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                        tenantId, companyId, "ID_UPLOAD"))
                .thenReturn(false);

        var response = service.create(request("ID_UPLOAD", null));

        assertThat(response.mandatory()).isTrue();
        assertThat(response.defaultOwner()).isEqualTo(OnboardingTaskOwner.HR);
        assertThat(response.active()).isTrue();
    }

    @Test
    void createHonorsAnExplicitFalseMandatoryFlag() {
        when(templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                        tenantId, companyId, "ID_UPLOAD"))
                .thenReturn(false);

        var response = service.create(request("ID_UPLOAD", false));

        assertThat(response.mandatory()).isFalse();
    }

    @Test
    void deleteThrowsNotFoundForAnUnknownTemplate() {
        UUID id = UUID.randomUUID();
        when(templates.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteChecksAccessForTheTemplatesCompany() {
        UUID id = UUID.randomUUID();
        OnboardingTaskTemplate t = new OnboardingTaskTemplate();
        t.setId(id);
        t.setCompanyId(companyId);
        when(templates.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(t));

        service.delete(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
        verify(templates).delete(t);
    }

    @Test
    void listForCompanyChecksAccessBeforeQuerying() {
        when(templates.findAllByTenantIdAndCompanyIdOrderBySortOrderAsc(tenantId, companyId))
                .thenReturn(List.of());

        service.listForCompany(tenantId, companyId);

        verify(guard).requireAccessForCompany(companyId);
    }
}
