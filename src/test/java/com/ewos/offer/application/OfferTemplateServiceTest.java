package com.ewos.offer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.offer.api.OfferMapper;
import com.ewos.offer.api.dto.CreateOfferTemplateRequest;
import com.ewos.offer.domain.OfferTemplate;
import com.ewos.offer.domain.events.OfferEvent;
import com.ewos.offer.infrastructure.persistence.OfferTemplateRepository;
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
class OfferTemplateServiceTest {

    @Mock OfferTemplateRepository templates;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private final OfferMapper mapper = new OfferMapper();

    private OfferTemplateService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OfferTemplateService(templates, mapper, events, guard);
    }

    private CreateOfferTemplateRequest createRequest() {
        return new CreateOfferTemplateRequest(
                tenantId,
                companyId,
                "STD-1",
                "Standard Offer",
                null,
                "Dear {{name}}",
                "USD",
                30,
                90,
                14,
                null);
    }

    @Test
    void createRejectsDuplicateCode() {
        when(templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "STD-1"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(templates, never()).save(any());
    }

    @Test
    void createDefaultsActiveTrueAndPublishesEvent() {
        when(templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "STD-1"))
                .thenReturn(false);
        when(templates.save(any(OfferTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.create(createRequest());

        assertThat(resp.active()).isTrue();
        assertThat(resp.defaultExpiryDays()).isEqualTo(14);
        verify(guard).requireAccessForCompany(companyId);
        verify(events).publishEvent(any(OfferEvent.class));
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
        OfferTemplate t = new OfferTemplate();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenantId);
        t.setCompanyId(companyId);
        when(templates.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));

        service.delete(tenantId, t.getId());

        verify(guard).requireAccessForCompany(companyId);
        verify(templates).delete(t);
    }
}
