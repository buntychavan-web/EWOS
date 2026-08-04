package com.ewos.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.interview.api.InterviewMapper;
import com.ewos.interview.api.dto.CreateInterviewTemplateRequest;
import com.ewos.interview.api.dto.UpdateInterviewTemplateRequest;
import com.ewos.interview.domain.InterviewTemplate;
import com.ewos.interview.domain.InterviewType;
import com.ewos.interview.domain.events.InterviewEvent;
import com.ewos.interview.infrastructure.persistence.InterviewTemplateRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class InterviewTemplateServiceTest {

    @Mock InterviewTemplateRepository templates;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private final InterviewMapper mapper = new InterviewMapper();

    private InterviewTemplateService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InterviewTemplateService(templates, mapper, events, guard);
    }

    private CreateInterviewTemplateRequest createRequest() {
        return new CreateInterviewTemplateRequest(
                tenantId,
                companyId,
                "TECH-1",
                "Technical Round",
                null,
                InterviewType.TECHNICAL,
                45,
                null,
                null);
    }

    private InterviewTemplate template(boolean active) {
        InterviewTemplate t = new InterviewTemplate();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenantId);
        t.setCompanyId(companyId);
        t.setActive(active);
        return t;
    }

    @Test
    void createRejectsDuplicateCode() {
        when(templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "TECH-1"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(templates, never()).save(any());
    }

    @Test
    void createDefaultsActiveTrueAndPublishesEvent() {
        when(templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "TECH-1"))
                .thenReturn(false);
        when(templates.save(any(InterviewTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.create(createRequest());

        assertThat(resp.active()).isTrue();
        assertThat(resp.defaultDurationMinutes()).isEqualTo(45);
        verify(guard).requireAccessForCompany(companyId);
        verify(events).publishEvent(any(InterviewEvent.class));
    }

    @Test
    void updatePublishesDeactivatedEventOnlyWhenFlagFlips() {
        InterviewTemplate t = template(true);
        when(templates.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));

        var req =
                new UpdateInterviewTemplateRequest(
                        "Technical Round", null, InterviewType.TECHNICAL, 45, null, false);

        service.update(tenantId, t.getId(), req);

        assertThat(t.isActive()).isFalse();
        verify(events, times(2)).publishEvent(any(InterviewEvent.class));
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(templates.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteChecksCompanyAccessBeforeDeleting() {
        InterviewTemplate t = template(true);
        when(templates.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));

        service.delete(tenantId, t.getId());

        verify(guard).requireAccessForCompany(companyId);
        verify(templates).delete(t);
    }
}
