package com.ewos.ats.application;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.CandidateResponse;
import com.ewos.ats.api.dto.ChangeCandidateStatusRequest;
import com.ewos.ats.api.dto.CreateCandidateRequest;
import com.ewos.ats.api.dto.CreateCandidateResult;
import com.ewos.ats.api.dto.DuplicateCandidateMatchResponse;
import com.ewos.ats.api.dto.RecordCandidateConsentRequest;
import com.ewos.ats.api.dto.UpdateCandidateRequest;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateNumberGenerator;
import com.ewos.ats.domain.CandidateStatus;
import com.ewos.ats.domain.DuplicateCandidateDetector;
import com.ewos.ats.domain.DuplicateCandidateMatch;
import com.ewos.ats.domain.PhoneNormalizer;
import com.ewos.ats.domain.TimelineEventType;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.domain.events.AtsEventType;
import com.ewos.ats.infrastructure.persistence.CandidateRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CandidateService {

    private final CandidateRepository candidates;
    private final EmployeeRepository employees;
    private final CandidateNumberGenerator numbers;
    private final DuplicateCandidateDetector duplicates;
    private final CandidateTimelineService timeline;
    private final AtsMapper mapper;
    private final ApplicationEventPublisher events;
    private final ClientAccessGuard guard;

    public CandidateService(
            CandidateRepository candidates,
            EmployeeRepository employees,
            CandidateNumberGenerator numbers,
            DuplicateCandidateDetector duplicates,
            CandidateTimelineService timeline,
            AtsMapper mapper,
            ApplicationEventPublisher events,
            ClientAccessGuard guard) {
        this.candidates = candidates;
        this.employees = employees;
        this.numbers = numbers;
        this.duplicates = duplicates;
        this.timeline = timeline;
        this.mapper = mapper;
        this.events = events;
        this.guard = guard;
    }

    public CreateCandidateResult create(CreateCandidateRequest req) {
        guard.requireAccessForCompany(req.companyId());
        String number =
                req.candidateNumber() == null || req.candidateNumber().isBlank()
                        ? numbers.generate(req.tenantId(), req.companyId())
                        : req.candidateNumber();
        if (candidates.existsByTenantIdAndCompanyIdAndCandidateNumberIgnoreCase(
                req.tenantId(), req.companyId(), number)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Candidate number already exists: " + number);
        }

        List<DuplicateCandidateMatch> dupes =
                duplicates.findDuplicates(req.tenantId(), req.email(), req.phone());

        Candidate c = new Candidate();
        c.setTenantId(req.tenantId());
        c.setCompanyId(req.companyId());
        c.setCandidateNumber(number);
        c.setFirstName(req.firstName());
        c.setMiddleName(req.middleName());
        c.setLastName(req.lastName());
        c.setEmail(normalizeEmail(req.email()));
        c.setPhone(req.phone());
        c.setPhoneDigits(PhoneNormalizer.digitsOnly(req.phone()));
        c.setDateOfBirth(req.dateOfBirth());
        c.setGender(req.gender());
        c.setNationality(req.nationality());
        c.setCurrentLocation(req.currentLocation());
        c.setCountry(req.country());
        c.setCurrentEmployer(req.currentEmployer());
        c.setCurrentDesignation(req.currentDesignation());
        c.setTotalExperienceMonths(req.totalExperienceMonths());
        c.setCurrentCtcCurrency(req.currentCtcCurrency());
        c.setCurrentCtcAmount(req.currentCtcAmount());
        c.setExpectedCtcCurrency(req.expectedCtcCurrency());
        c.setExpectedCtcAmount(req.expectedCtcAmount());
        c.setNoticePeriodDays(req.noticePeriodDays());
        c.setSource(req.source());
        c.setSourceDetails(req.sourceDetails());
        c.setReferrerEmployee(resolveEmployee(req.tenantId(), req.referrerEmployeeId()));
        boolean internal = Boolean.TRUE.equals(req.internal()) || req.internalEmployeeId() != null;
        c.setInternal(internal);
        c.setInternalEmployee(
                internal ? resolveEmployee(req.tenantId(), req.internalEmployeeId()) : null);
        c.setStatus(CandidateStatus.NEW);
        c.setLinkedinUrl(req.linkedinUrl());
        c.setGithubUrl(req.githubUrl());
        c.setPortfolioUrl(req.portfolioUrl());
        c.setSummary(req.summary());
        c = candidates.save(c);

        timeline.record(c, null, TimelineEventType.CANDIDATE_CREATED, "Candidate created", null);
        publish(AtsEventType.CANDIDATE_CREATED, c, null);

        List<DuplicateCandidateMatchResponse> dupeResponses =
                dupes.stream().map(mapper::toResponse).toList();
        return new CreateCandidateResult(mapper.toResponse(c), dupeResponses);
    }

    public CandidateResponse update(UUID tenantId, UUID id, UpdateCandidateRequest req) {
        Candidate c = require(tenantId, id);
        assertMutable(c);
        c.setFirstName(req.firstName());
        c.setMiddleName(req.middleName());
        c.setLastName(req.lastName());
        c.setEmail(normalizeEmail(req.email()));
        c.setPhone(req.phone());
        c.setPhoneDigits(PhoneNormalizer.digitsOnly(req.phone()));
        c.setDateOfBirth(req.dateOfBirth());
        c.setGender(req.gender());
        c.setNationality(req.nationality());
        c.setCurrentLocation(req.currentLocation());
        c.setCountry(req.country());
        c.setCurrentEmployer(req.currentEmployer());
        c.setCurrentDesignation(req.currentDesignation());
        c.setTotalExperienceMonths(req.totalExperienceMonths());
        c.setCurrentCtcCurrency(req.currentCtcCurrency());
        c.setCurrentCtcAmount(req.currentCtcAmount());
        c.setExpectedCtcCurrency(req.expectedCtcCurrency());
        c.setExpectedCtcAmount(req.expectedCtcAmount());
        c.setNoticePeriodDays(req.noticePeriodDays());
        c.setSource(req.source());
        c.setSourceDetails(req.sourceDetails());
        c.setReferrerEmployee(resolveEmployee(tenantId, req.referrerEmployeeId()));
        boolean internal = Boolean.TRUE.equals(req.internal()) || req.internalEmployeeId() != null;
        c.setInternal(internal);
        c.setInternalEmployee(
                internal ? resolveEmployee(tenantId, req.internalEmployeeId()) : null);
        c.setLinkedinUrl(req.linkedinUrl());
        c.setGithubUrl(req.githubUrl());
        c.setPortfolioUrl(req.portfolioUrl());
        c.setSummary(req.summary());

        timeline.record(c, null, TimelineEventType.CANDIDATE_UPDATED, "Candidate updated", null);
        publish(AtsEventType.CANDIDATE_UPDATED, c, null);
        return mapper.toResponse(c);
    }

    public CandidateResponse changeStatus(
            UUID tenantId, UUID id, ChangeCandidateStatusRequest req) {
        Candidate c = require(tenantId, id);
        if (c.getStatus() == req.status()) {
            return mapper.toResponse(c);
        }
        c.setStatus(req.status());
        timeline.record(
                c,
                null,
                TimelineEventType.CANDIDATE_STATUS_CHANGED,
                "Status changed to " + req.status(),
                req.reason());
        AtsEventType type =
                switch (req.status()) {
                    case ARCHIVED -> AtsEventType.CANDIDATE_ARCHIVED;
                    case BLACKLISTED -> AtsEventType.CANDIDATE_BLACKLISTED;
                    default -> AtsEventType.CANDIDATE_STATUS_CHANGED;
                };
        publish(type, c, req.reason());
        return mapper.toResponse(c);
    }

    /**
     * Records a consent decision (given or withdrawn) and, optionally, the retention basis that
     * follows from it. {@code consentSource} must be present when granting consent — the service
     * has no reasonable default for how consent was captured. Withdrawing consent that was never
     * given is rejected: there is nothing to withdraw.
     */
    public CandidateResponse recordConsent(
            UUID tenantId, UUID id, RecordCandidateConsentRequest req) {
        Candidate c = require(tenantId, id);
        if (Boolean.TRUE.equals(req.consentGiven())) {
            if (req.consentSource() == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "consentSource is required when granting consent");
            }
            c.setConsentGiven(true);
            c.setConsentGivenAt(Instant.now());
            c.setConsentWithdrawnAt(null);
            c.setConsentSource(req.consentSource());
            timeline.record(
                    c,
                    null,
                    TimelineEventType.CANDIDATE_CONSENT_RECORDED,
                    "Consent recorded (" + req.consentSource() + ")",
                    null);
            publish(AtsEventType.CANDIDATE_CONSENT_RECORDED, c, req.consentSource().toString());
        } else {
            if (!c.isConsentGiven()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Consent has not been given yet — nothing to withdraw");
            }
            c.setConsentGiven(false);
            c.setConsentWithdrawnAt(Instant.now());
            timeline.record(
                    c,
                    null,
                    TimelineEventType.CANDIDATE_CONSENT_WITHDRAWN,
                    "Consent withdrawn",
                    null);
            publish(AtsEventType.CANDIDATE_CONSENT_WITHDRAWN, c, null);
        }
        if (req.retentionExpiresAt() != null && req.retentionExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "retentionExpiresAt must not be in the past");
        }
        if (req.retentionPolicyCode() != null) {
            c.setRetentionPolicyCode(req.retentionPolicyCode());
        }
        if (req.retentionExpiresAt() != null) {
            c.setRetentionExpiresAt(req.retentionExpiresAt());
        }
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public CandidateResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public Page<CandidateResponse> list(
            UUID tenantId, UUID companyId, CandidateStatus status, Pageable page) {
        guard.requireAccessForCompany(companyId);
        Page<Candidate> results =
                status == null
                        ? candidates.findAllByTenantIdAndCompanyId(tenantId, companyId, page)
                        : candidates.findAllByTenantIdAndCompanyIdAndStatus(
                                tenantId, companyId, status, page);
        return results.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<DuplicateCandidateMatchResponse> checkDuplicates(
            UUID tenantId, String email, String phone) {
        return duplicates.findDuplicates(tenantId, email, phone).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Resolves a candidate and enforces the Chinese Wall — the single choke point every other ATS
     * service (notes, tags, documents, résumés, communications, job applications) also calls
     * through, so guarding here closes the gap for all of them at once.
     */
    public Candidate require(UUID tenantId, UUID id) {
        Candidate c =
                candidates
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Candidate not found"));
        guard.requireAccessForCompany(c.getCompanyId());
        return c;
    }

    private void assertMutable(Candidate c) {
        if (c.getStatus() == CandidateStatus.BLACKLISTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Blacklisted candidates cannot be edited");
        }
    }

    private Employee resolveEmployee(UUID tenantId, UUID employeeId) {
        if (employeeId == null) {
            return null;
        }
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Employee not found"));
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank()
                ? null
                : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void publish(AtsEventType type, Candidate c, String detail) {
        events.publishEvent(
                new AtsEvent(
                        type,
                        c.getTenantId(),
                        c.getCompanyId(),
                        c.getId(),
                        null,
                        null,
                        null,
                        detail,
                        currentActor(),
                        Instant.now()));
    }

    static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
