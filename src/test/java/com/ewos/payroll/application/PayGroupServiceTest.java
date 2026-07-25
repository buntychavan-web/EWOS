package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreatePayGroupRequest;
import com.ewos.payroll.api.dto.PayGroupResponse;
import com.ewos.payroll.api.dto.UpdatePayGroupRequest;
import com.ewos.payroll.domain.PayGroup;
import com.ewos.payroll.domain.PayrollFrequency;
import com.ewos.payroll.infrastructure.persistence.PayGroupRepository;
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
 * Sprint 14.2 — verifies {@link ClientAccessGuard} is consulted on every entry point, the same
 * pattern applied uniformly across the Payroll module's other 16 company-scoped services.
 */
@ExtendWith(MockitoExtension.class)
class PayGroupServiceTest {

    @Mock PayGroupRepository repository;
    @Mock ClientAccessGuard guard;

    private PayGroupService service;

    @BeforeEach
    void setUp() {
        service = new PayGroupService(repository, new PayrollMapper(), guard);
        org.mockito.Mockito.lenient()
                .when(repository.save(any(PayGroup.class)))
                .thenAnswer(
                        inv -> {
                            PayGroup g = inv.getArgument(0);
                            if (g.getId() == null) {
                                g.setId(UUID.randomUUID());
                            }
                            return g;
                        });
    }

    @Test
    void createChecksAccessForTheRequestedCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        service.create(
                new CreatePayGroupRequest(
                        tenantId,
                        companyId,
                        "PG-01",
                        "Executives",
                        null,
                        PayrollFrequency.MONTHLY,
                        "USD",
                        1,
                        null));

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createRejectedWhenCallerLacksCompanyAccess() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreatePayGroupRequest(
                                                tenantId,
                                                companyId,
                                                "PG-01",
                                                "Execs",
                                                null,
                                                PayrollFrequency.MONTHLY,
                                                "USD",
                                                1,
                                                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void getByIdChecksAccessOnEveryCallNotJustOnCacheMiss() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayGroup g = new PayGroup();
        g.setId(id);
        g.setCompanyId(companyId);
        g.setCode("PG-01");
        g.setName("Execs");
        g.setFrequency(PayrollFrequency.MONTHLY);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(g));

        PayGroupResponse r = service.getById(tenantId, id);

        assertThat(r.companyId()).isEqualTo(companyId);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void updateChecksAccessForTheExistingRecordsCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayGroup g = new PayGroup();
        g.setId(id);
        g.setCompanyId(companyId);
        g.setFrequency(PayrollFrequency.MONTHLY);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(g));

        service.update(
                tenantId, id, new UpdatePayGroupRequest("New name", null, null, null, null, null));

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void forCompanyChecksAccessBeforeQuerying() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.findAllByTenantIdAndCompanyIdOrderByNameAsc(tenantId, companyId))
                .thenReturn(List.of());

        service.forCompany(tenantId, companyId);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void deleteChecksAccessForTheExistingRecordsCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayGroup g = new PayGroup();
        g.setId(id);
        g.setCompanyId(companyId);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(g));

        service.delete(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
        verify(repository).delete(g);
    }
}
