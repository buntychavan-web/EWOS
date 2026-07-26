package com.ewos.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateNumberGenerator;
import com.ewos.ats.domain.DuplicateCandidateDetector;
import com.ewos.ats.infrastructure.persistence.CandidateRepository;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
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

/**
 * {@link CandidateService#require} is the single choke point every other ATS service (notes,
 * tags, documents, résumés, communications, job applications) resolves a candidate through — these
 * tests exist specifically to prove the Sprint 1.2 ClientAccessGuard wiring at that choke point,
 * not to cover the module end-to-end (no test suite pre-existed for this module).
 */
@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock CandidateRepository candidates;
    @Mock EmployeeRepository employees;
    @Mock CandidateNumberGenerator numbers;
    @Mock DuplicateCandidateDetector duplicates;
    @Mock CandidateTimelineService timeline;
    @Mock ClientAccessGuard guard;
    @Mock ApplicationEventPublisher events;

    private CandidateService service;

    @BeforeEach
    void setUp() {
        service =
                new CandidateService(
                        candidates, employees, numbers, duplicates, timeline, new AtsMapper(), events, guard);
    }

    @Test
    void requireGuardsTheResolvedCandidatesCompany() {
        UUID tenant = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        Candidate c = new Candidate();
        c.setId(id);
        c.setCompanyId(company);
        when(candidates.findByIdAndTenantId(id, tenant)).thenReturn(Optional.of(c));

        Candidate result = service.require(tenant, id);

        assertThat(result).isSameAs(c);
        verify(guard).requireAccessForCompany(company);
    }

    @Test
    void requireIsBlockedWhenCallerLacksClientAccess() {
        UUID tenant = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        Candidate c = new Candidate();
        c.setId(id);
        c.setCompanyId(company);
        when(candidates.findByIdAndTenantId(id, tenant)).thenReturn(Optional.of(c));
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized for this company"))
                .when(guard)
                .requireAccessForCompany(company);

        assertThatThrownBy(() -> service.require(tenant, id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void requireRejectsUnknownCandidateBeforeGuardIsConsulted() {
        UUID tenant = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(candidates.findByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(tenant, id)).isInstanceOf(ApiException.class);

        verify(guard, org.mockito.Mockito.never()).requireAccessForCompany(org.mockito.ArgumentMatchers.any());
    }
}
