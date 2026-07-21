package com.ewos.onboarding.application;

import com.ewos.ats.domain.Candidate;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.offer.domain.EmployeeIdGenerator;
import com.ewos.offer.domain.Offer;
import com.ewos.offer.domain.OfferStatus;
import com.ewos.offer.infrastructure.persistence.OfferRepository;
import com.ewos.onboarding.api.dto.CandidateConversionResponse;
import com.ewos.onboarding.api.dto.ConvertCandidateRequest;
import com.ewos.onboarding.api.dto.CreateOnboardingPlanRequest;
import com.ewos.onboarding.domain.EmployeeProvisioningService;
import com.ewos.onboarding.domain.OnboardingPlan;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.shared.exception.ApiException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Converts a T2 {@link Candidate} into an {@link Employee} once their T4 offer is ACCEPTED. This is
 * the "handover on joining" that the Business Bible calls for — a single call materialises the
 * employee record, provisions login / email via the {@link EmployeeProvisioningService} contract,
 * and hands off to the T5 onboarding plan.
 */
@Service
@Transactional
public class CandidateConversionService {

    private final OfferRepository offers;
    private final EmployeeRepository employees;
    private final OrganizationUnitRepository orgUnits;
    private final EmployeeIdGenerator employeeIdGenerator;
    private final EmployeeProvisioningService provisioning;
    private final OnboardingPlanService plans;

    public CandidateConversionService(
            OfferRepository offers,
            EmployeeRepository employees,
            OrganizationUnitRepository orgUnits,
            EmployeeIdGenerator employeeIdGenerator,
            EmployeeProvisioningService provisioning,
            OnboardingPlanService plans) {
        this.offers = offers;
        this.employees = employees;
        this.orgUnits = orgUnits;
        this.employeeIdGenerator = employeeIdGenerator;
        this.provisioning = provisioning;
        this.plans = plans;
    }

    public CandidateConversionResponse convert(UUID tenantId, ConvertCandidateRequest req) {
        Offer offer =
                offers.findByIdAndTenantId(req.offerId(), tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found"));
        if (offer.getStatus() != OfferStatus.ACCEPTED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer must be ACCEPTED to convert candidate (current: "
                            + offer.getStatus()
                            + ")");
        }
        Candidate candidate = offer.getCandidate();
        if (candidate == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Offer has no candidate — cannot convert to employee");
        }

        String employeeNumber =
                req.employeeNumber() == null || req.employeeNumber().isBlank()
                        ? employeeIdGenerator.generate(tenantId, offer.getCompanyId())
                        : req.employeeNumber();
        if (employees.existsByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                tenantId, offer.getCompanyId(), employeeNumber)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Employee number already exists: " + employeeNumber);
        }

        String workEmail =
                req.workEmail() == null || req.workEmail().isBlank()
                        ? deriveWorkEmail(candidate)
                        : req.workEmail();
        LocalDate joiningDate =
                req.joiningDate() != null ? req.joiningDate() : offer.getTargetJoiningDate();
        if (joiningDate == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Joining date is required (offer + request both empty)");
        }

        Employee e = new Employee();
        e.setTenantId(tenantId);
        e.setCompanyId(offer.getCompanyId());
        e.setEmployeeNumber(employeeNumber);
        e.setFirstName(candidate.getFirstName());
        e.setMiddleName(candidate.getMiddleName());
        e.setLastName(candidate.getLastName());
        e.setDisplayName(candidate.fullName());
        e.setWorkEmail(workEmail);
        e.setPersonalEmail(candidate.getEmail());
        e.setPhone(candidate.getPhone());
        e.setDateOfBirth(candidate.getDateOfBirth());
        if (req.primaryOrgUnitId() != null) {
            e.setPrimaryOrgUnit(
                    orgUnits.findByIdAndTenantId(req.primaryOrgUnitId(), tenantId)
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "Primary org unit not found")));
        }
        if (req.managerEmployeeId() != null) {
            e.setManager(
                    employees
                            .findByIdAndTenantId(req.managerEmployeeId(), tenantId)
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.BAD_REQUEST, "Manager not found")));
        }
        e.setHireDate(joiningDate);
        e.setStatus(EmployeeStatus.ACTIVE);
        e = employees.save(e);

        String loginRef = provisioning.provisionLogin(e);
        String emailRef = provisioning.provisionEmail(e);

        // Hand off to onboarding — idempotent by employee.
        CreateOnboardingPlanRequest planReq =
                new CreateOnboardingPlanRequest(
                        tenantId,
                        offer.getCompanyId(),
                        e.getId(),
                        offer.getId(),
                        null,
                        joiningDate,
                        req.managerEmployeeId(),
                        null,
                        null);
        OnboardingPlan plan = plans.createInternal(planReq);

        return new CandidateConversionResponse(
                e.getId(), employeeNumber, workEmail, loginRef, emailRef, plan.getId());
    }

    private static String deriveWorkEmail(Candidate c) {
        String first =
                c.getFirstName() == null ? "" : c.getFirstName().toLowerCase(java.util.Locale.ROOT);
        String last =
                c.getLastName() == null ? "" : c.getLastName().toLowerCase(java.util.Locale.ROOT);
        return (first + "." + last + "@" + "corp.example").replaceAll("\\s+", "");
    }
}
