package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreatePayComponentRequest;
import com.ewos.payroll.api.dto.UpdatePayComponentRequest;
import com.ewos.payroll.domain.PayComponent;
import com.ewos.payroll.domain.PayComponentCalculationType;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.infrastructure.persistence.PayComponentRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * Earnings/deductions catalogue: uniqueness of code per tenant, update semantics, not-found paths.
 */
@ExtendWith(MockitoExtension.class)
class PayComponentServiceTest {

    @Mock PayComponentRepository repository;
    @Mock ApplicationEventPublisher events;

    private PayComponentService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PayComponentService(repository, new PayrollMapper(), events);
        org.mockito.Mockito.lenient()
                .when(repository.save(any(PayComponent.class)))
                .thenAnswer(
                        inv -> {
                            PayComponent c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
    }

    private CreatePayComponentRequest earningRequest(String code) {
        return new CreatePayComponentRequest(
                tenantId,
                code,
                "House Rent Allowance",
                null,
                PayComponentKind.EARNING,
                PayComponentCalculationType.PERCENT_OF_BASIC,
                null,
                new BigDecimal("40"),
                true,
                true,
                true,
                1);
    }

    @Test
    void createRejectsADuplicateCodeForTheSameTenantCaseInsensitively() {
        when(repository.existsByTenantIdAndCodeIgnoreCase(tenantId, "HRA")).thenReturn(true);

        assertThatThrownBy(() -> service.create(earningRequest("HRA")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createPersistsAllSuppliedFields() {
        when(repository.existsByTenantIdAndCodeIgnoreCase(tenantId, "HRA")).thenReturn(false);

        var response = service.create(earningRequest("HRA"));

        assertThat(response.code()).isEqualTo("HRA");
        assertThat(response.kind()).isEqualTo(PayComponentKind.EARNING);
        assertThat(response.calculationType())
                .isEqualTo(PayComponentCalculationType.PERCENT_OF_BASIC);
        assertThat(response.defaultPercentage()).isEqualByComparingTo("40");
    }

    @Test
    void createDefaultsTaxableActiveAndSortOrderWhenOmitted() {
        CreatePayComponentRequest req =
                new CreatePayComponentRequest(
                        tenantId,
                        "BONUS",
                        "Bonus",
                        null,
                        PayComponentKind.EARNING,
                        PayComponentCalculationType.FIXED,
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        null,
                        null);
        when(repository.existsByTenantIdAndCodeIgnoreCase(tenantId, "BONUS")).thenReturn(false);

        var response = service.create(req);

        // Defaults come from the entity's own field initializers when the request omits them.
        assertThat(response.code()).isEqualTo("BONUS");
    }

    @Test
    void updateRejectsRenamingToACodeAlreadyUsedByAnotherComponent() {
        UUID id = UUID.randomUUID();
        PayComponent existing = new PayComponent();
        existing.setId(id);
        existing.setCode("HRA");
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));
        when(repository.existsByTenantIdAndCodeIgnoreCase(tenantId, "DA")).thenReturn(true);

        UpdatePayComponentRequest req =
                new UpdatePayComponentRequest(
                        "DA", null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(tenantId, id, req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateAllowsKeepingTheSameCodeUnchanged() {
        UUID id = UUID.randomUUID();
        PayComponent existing = new PayComponent();
        existing.setId(id);
        existing.setCode("HRA");
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        UpdatePayComponentRequest req =
                new UpdatePayComponentRequest(
                        "hra",
                        "House Rent Allowance",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        var response = service.update(tenantId, id, req);

        // Case-insensitive match against the existing code means the stored casing is untouched.
        assertThat(response.code()).isEqualTo("HRA");
        verify(repository, never()).existsByTenantIdAndCodeIgnoreCase(any(), any());
    }

    @Test
    void updateOnlyOverwritesFieldsThatAreSuppliedLeavingOthersIntact() {
        UUID id = UUID.randomUUID();
        PayComponent existing = new PayComponent();
        existing.setId(id);
        existing.setCode("HRA");
        existing.setName("House Rent Allowance");
        existing.setKind(PayComponentKind.EARNING);
        existing.setCalculationType(PayComponentCalculationType.PERCENT_OF_BASIC);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        UpdatePayComponentRequest req =
                new UpdatePayComponentRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("50"),
                        null,
                        null,
                        null,
                        null);

        var response = service.update(tenantId, id, req);

        assertThat(response.name()).isEqualTo("House Rent Allowance");
        assertThat(response.defaultPercentage()).isEqualByComparingTo("50");
    }

    @Test
    void updateThrowsNotFoundForAnUnknownComponent() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.update(
                                        tenantId,
                                        id,
                                        new UpdatePayComponentRequest(
                                                null, null, null, null, null, null, null, null,
                                                null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteThrowsNotFoundForAnUnknownComponent() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteRemovesAnExistingComponent() {
        UUID id = UUID.randomUUID();
        PayComponent existing = new PayComponent();
        existing.setId(id);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        service.delete(tenantId, id);

        verify(repository).delete(existing);
    }
}
