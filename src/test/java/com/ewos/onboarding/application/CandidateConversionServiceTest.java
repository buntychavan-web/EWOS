package com.ewos.onboarding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.ats.domain.Candidate;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.offer.domain.EmployeeIdGenerator;
import com.ewos.offer.domain.Offer;
import com.ewos.offer.domain.OfferStatus;
import com.ewos.offer.infrastructure.persistence.OfferRepository;
import com.ewos.onboarding.api.dto.ConvertCandidateRequest;
import com.ewos.onboarding.domain.EmployeeProvisioningService;
import com.ewos.onboarding.domain.OnboardingPlan;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Candidate-to-employee conversion — the "New Joiner" handover: only ACCEPTED offers convert,
 * employee-number generation/collision, work-email derivation, joining-date resolution, and the
 * idempotent hand-off into an onboarding plan.
 */
@ExtendWith(MockitoExtension.class)
class CandidateConversionServiceTest {

    @Mock OfferRepository offers;
    @Mock EmployeeRepository employees;
    @Mock OrganizationUnitRepository orgUnits;
    @Mock EmployeeIdGenerator employeeIdGenerator;
    @Mock EmployeeProvisioningService provisioning;
    @Mock OnboardingPlanService plans;
    @Mock ClientAccessGuard guard;

    private CandidateConversionService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID offerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new CandidateConversionService(
                        offers,
                        employees,
                        orgUnits,
                        employeeIdGenerator,
                        provisioning,
                        plans,
                        guard);
        org.mockito.Mockito.lenient()
                .when(employees.save(any(Employee.class)))
                .thenAnswer(
                        inv -> {
                            Employee e = inv.getArgument(0);
                            if (e.getId() == null) {
                                e.setId(UUID.randomUUID());
                            }
                            return e;
                        });
    }

    private Candidate candidate() {
        Candidate c = new Candidate();
        c.setFirstName("Asha");
        c.setLastName("Rao");
        c.setEmail("asha.personal@example.com");
        c.setPhone("+911234567890");
        c.setDateOfBirth(LocalDate.of(1995, 6, 15));
        return c;
    }

    private Offer acceptedOffer(LocalDate targetJoiningDate) {
        Offer o = new Offer();
        o.setId(offerId);
        o.setTenantId(tenantId);
        o.setCompanyId(companyId);
        o.setStatus(OfferStatus.ACCEPTED);
        o.setCandidate(candidate());
        o.setTargetJoiningDate(targetJoiningDate);
        return o;
    }

    private ConvertCandidateRequest request() {
        return new ConvertCandidateRequest(offerId, null, null, null, null, null);
    }

    @Test
    void convertThrowsNotFoundForAnUnknownOffer() {
        when(offers.findByIdAndTenantId(offerId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convert(tenantId, request()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void convertRejectedWhenOfferIsNotAccepted() {
        Offer offer = acceptedOffer(LocalDate.of(2026, 4, 1));
        offer.setStatus(OfferStatus.EXTENDED);
        when(offers.findByIdAndTenantId(offerId, tenantId)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.convert(tenantId, request()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(employees, never()).save(any());
    }

    @Test
    void convertRejectedWhenTheOfferHasNoCandidate() {
        Offer offer = acceptedOffer(LocalDate.of(2026, 4, 1));
        offer.setCandidate(null);
        when(offers.findByIdAndTenantId(offerId, tenantId)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.convert(tenantId, request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no candidate");
    }

    @Test
    void convertGeneratesAnEmployeeNumberWhenNoneSupplied() {
        Offer offer = acceptedOffer(LocalDate.of(2026, 4, 1));
        when(offers.findByIdAndTenantId(offerId, tenantId)).thenReturn(Optional.of(offer));
        when(employeeIdGenerator.generate(tenantId, companyId)).thenReturn("EMP-202604-000123");
        when(employees.existsByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "EMP-202604-000123"))
                .thenReturn(false);
        when(plans.createInternal(any())).thenReturn(planWithId());

        var response = service.convert(tenantId, request());

        assertThat(response.employeeNumber()).isEqualTo("EMP-202604-000123");
    }

    @Test
    void convertRejectsACollidingExplicitEmployeeNumber() {
        Offer offer = acceptedOffer(LocalDate.of(2026, 4, 1));
        when(offers.findByIdAndTenantId(offerId, tenantId)).thenReturn(Optional.of(offer));
        when(employees.existsByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "EMP-0001"))
                .thenReturn(true);

        ConvertCandidateRequest req =
                new ConvertCandidateRequest(offerId, "EMP-0001", null, null, null, null);

        assertThatThrownBy(() -> service.convert(tenantId, req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void convertDerivesTheWorkEmailFromTheCandidateNameWhenNoneSupplied() {
        Offer offer = acceptedOffer(LocalDate.of(2026, 4, 1));
        when(offers.findByIdAndTenantId(offerId, tenantId)).thenReturn(Optional.of(offer));
        when(employeeIdGenerator.generate(tenantId, companyId)).thenReturn("EMP-1");
        when(plans.createInternal(any())).thenReturn(planWithId());

        var response = service.convert(tenantId, request());

        assertThat(response.workEmail()).isEqualTo("asha.rao@corp.example");
    }

    @Test
    void convertHonorsAnExplicitlySuppliedWorkEmail() {
        Offer offer = acceptedOffer(LocalDate.of(2026, 4, 1));
        when(offers.findByIdAndTenantId(offerId, tenantId)).thenReturn(Optional.of(offer));
        when(employeeIdGenerator.generate(tenantId, companyId)).thenReturn("EMP-1");
        when(plans.createInternal(any())).thenReturn(planWithId());

        ConvertCandidateRequest req =
                new ConvertCandidateRequest(
                        offerId, null, "a.rao@override.example", null, null, null);
        var response = service.convert(tenantId, req);

        assertThat(response.workEmail()).isEqualTo("a.rao@override.example");
    }

    @Test
    void convertFallsBackToTheOffersTargetJoiningDateWhenRequestOmitsIt() {
        Offer offer = acceptedOffer(LocalDate.of(2026, 5, 1));
        when(offers.findByIdAndTenantId(offerId, tenantId)).thenReturn(Optional.of(offer));
        when(employeeIdGenerator.generate(tenantId, companyId)).thenReturn("EMP-1");
        OnboardingPlan plan = planWithId();
        org.mockito.ArgumentCaptor<com.ewos.onboarding.api.dto.CreateOnboardingPlanRequest> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.ewos.onboarding.api.dto.CreateOnboardingPlanRequest.class);
        when(plans.createInternal(captor.capture())).thenReturn(plan);

        service.convert(tenantId, request());

        assertThat(captor.getValue().joiningDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void convertRejectedWhenNeitherRequestNorOfferSuppliesAJoiningDate() {
        Offer offer = acceptedOffer(null);
        when(offers.findByIdAndTenantId(offerId, tenantId)).thenReturn(Optional.of(offer));
        when(employeeIdGenerator.generate(tenantId, companyId)).thenReturn("EMP-1");

        assertThatThrownBy(() -> service.convert(tenantId, request()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void convertHandsOffToOnboardingIdempotentlyAndReturnsThePlanId() {
        Offer offer = acceptedOffer(LocalDate.of(2026, 4, 1));
        when(offers.findByIdAndTenantId(offerId, tenantId)).thenReturn(Optional.of(offer));
        when(employeeIdGenerator.generate(tenantId, companyId)).thenReturn("EMP-1");
        when(provisioning.provisionLogin(any())).thenReturn("login-ref-1");
        when(provisioning.provisionEmail(any())).thenReturn("email-ref-1");
        OnboardingPlan plan = planWithId();
        when(plans.createInternal(any())).thenReturn(plan);

        var response = service.convert(tenantId, request());

        assertThat(response.planId()).isEqualTo(plan.getId());
        assertThat(response.provisionedLoginRef()).isEqualTo("login-ref-1");
        assertThat(response.provisionedEmailRef()).isEqualTo("email-ref-1");
    }

    private static OnboardingPlan planWithId() {
        OnboardingPlan plan = new OnboardingPlan();
        plan.setId(UUID.randomUUID());
        return plan;
    }
}
