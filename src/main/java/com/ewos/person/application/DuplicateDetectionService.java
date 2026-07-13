package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.person.api.dto.DuplicateCheckRequest;
import com.ewos.person.api.dto.DuplicateCheckResponse;
import com.ewos.person.api.dto.DuplicateMatch;
import com.ewos.person.domain.IdentityDocumentKind;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonContact;
import com.ewos.person.domain.PersonDuplicateRule;
import com.ewos.person.domain.PersonIdentityDocument;
import com.ewos.person.domain.PersonVersion;
import com.ewos.person.infrastructure.persistence.PersonContactRepository;
import com.ewos.person.infrastructure.persistence.PersonDuplicateRuleRepository;
import com.ewos.person.infrastructure.persistence.PersonIdentityDocumentRepository;
import com.ewos.person.infrastructure.persistence.PersonVersionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the enabled duplicate-detection rules for a tenant and returns match candidates. Rules and
 * their weights are configured per tenant in {@code person_duplicate_rules} — nothing is hardcoded.
 */
@Service
@Transactional(readOnly = true)
public class DuplicateDetectionService {

    private final PersonDuplicateRuleRepository ruleRepository;
    private final PersonIdentityDocumentRepository documentRepository;
    private final PersonContactRepository contactRepository;
    private final PersonVersionRepository versionRepository;
    private final TenantResolver tenantResolver;

    public DuplicateDetectionService(
            PersonDuplicateRuleRepository ruleRepository,
            PersonIdentityDocumentRepository documentRepository,
            PersonContactRepository contactRepository,
            PersonVersionRepository versionRepository,
            TenantResolver tenantResolver) {
        this.ruleRepository = ruleRepository;
        this.documentRepository = documentRepository;
        this.contactRepository = contactRepository;
        this.versionRepository = versionRepository;
        this.tenantResolver = tenantResolver;
    }

    public DuplicateCheckResponse check(DuplicateCheckRequest req) {
        Tenant tenant = tenantResolver.resolve(req.tenantId());
        List<PersonDuplicateRule> rules =
                ruleRepository.findByTenantAndEnabledTrueOrderByWeightDesc(tenant);
        if (rules.isEmpty()) {
            return new DuplicateCheckResponse(List.of());
        }

        Map<UUID, DuplicateMatch> best = new LinkedHashMap<>();
        for (PersonDuplicateRule rule : rules) {
            for (DuplicateMatch m : runRule(rule, req)) {
                DuplicateMatch prior = best.get(m.personId());
                if (prior == null || m.weight() > prior.weight()) {
                    best.put(m.personId(), m);
                }
            }
        }

        List<DuplicateMatch> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparingInt(DuplicateMatch::weight).reversed());
        return new DuplicateCheckResponse(out);
    }

    private List<DuplicateMatch> runRule(PersonDuplicateRule rule, DuplicateCheckRequest req) {
        return switch (rule.getRuleKind()) {
            case PAN -> matchByDocument(rule, IdentityDocumentKind.PAN, req.pan());
            case AADHAAR -> matchByDocument(rule, IdentityDocumentKind.AADHAAR, req.aadhaar());
            case PASSPORT -> matchByDocument(rule, IdentityDocumentKind.PASSPORT, req.passport());
            case MOBILE -> matchByMobile(rule, req.mobile());
            case EMAIL -> matchByEmail(rule, req.email());
            case NAME_DOB ->
                    matchByNameAndDob(rule, req.firstName(), req.lastName(), req.dateOfBirth());
        };
    }

    private List<DuplicateMatch> matchByDocument(
            PersonDuplicateRule rule, IdentityDocumentKind kind, String number) {
        if (number == null || number.isBlank()) {
            return List.of();
        }
        List<PersonIdentityDocument> hits =
                documentRepository.findByDocumentKindAndDocumentNumber(kind, number).stream()
                        .toList();
        List<DuplicateMatch> out = new ArrayList<>();
        for (PersonIdentityDocument d : hits) {
            Person p = d.getPerson();
            out.add(
                    new DuplicateMatch(
                            p.getId(),
                            p.getGroupPersonId(),
                            fullName(p),
                            rule.getRuleKind(),
                            number,
                            rule.getWeight()));
        }
        return out;
    }

    private List<DuplicateMatch> matchByMobile(PersonDuplicateRule rule, String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return List.of();
        }
        List<DuplicateMatch> out = new ArrayList<>();
        for (PersonContact c : contactRepository.findByPersonalMobile(mobile)) {
            Person p = c.getPerson();
            out.add(
                    new DuplicateMatch(
                            p.getId(),
                            p.getGroupPersonId(),
                            fullName(p),
                            rule.getRuleKind(),
                            mobile,
                            rule.getWeight()));
        }
        return out;
    }

    private List<DuplicateMatch> matchByEmail(PersonDuplicateRule rule, String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        List<DuplicateMatch> out = new ArrayList<>();
        for (PersonContact c : contactRepository.findByPersonalEmailIgnoreCase(email)) {
            Person p = c.getPerson();
            out.add(
                    new DuplicateMatch(
                            p.getId(),
                            p.getGroupPersonId(),
                            fullName(p),
                            rule.getRuleKind(),
                            email.toLowerCase(Locale.ROOT),
                            rule.getWeight()));
        }
        return out;
    }

    private List<DuplicateMatch> matchByNameAndDob(
            PersonDuplicateRule rule, String firstName, String lastName, LocalDate dob) {
        if (firstName == null || lastName == null || dob == null) {
            return List.of();
        }
        // Cheap path: filter in-memory over the open versions. HR-scale tenants keep this fast.
        List<PersonVersion> all = versionRepository.findAll();
        List<DuplicateMatch> out = new ArrayList<>();
        String fn = firstName.toLowerCase(Locale.ROOT).trim();
        String ln = lastName.toLowerCase(Locale.ROOT).trim();
        for (PersonVersion v : all) {
            if (v.getEffectiveTo() != null) {
                continue;
            }
            if (v.getDateOfBirth() == null || !v.getDateOfBirth().equals(dob)) {
                continue;
            }
            if (!v.getFirstName().toLowerCase(Locale.ROOT).equals(fn)) {
                continue;
            }
            if (!v.getLastName().toLowerCase(Locale.ROOT).equals(ln)) {
                continue;
            }
            Person p = v.getPerson();
            out.add(
                    new DuplicateMatch(
                            p.getId(),
                            p.getGroupPersonId(),
                            firstName + " " + lastName,
                            rule.getRuleKind(),
                            firstName + " " + lastName + " / " + dob,
                            rule.getWeight()));
        }
        return out;
    }

    private String fullName(Person p) {
        return versionRepository
                .findByPersonAndEffectiveToIsNull(p)
                .map(v -> (v.getFirstName() + " " + v.getLastName()).trim())
                .orElse(p.getGroupPersonId());
    }

    /**
     * Validates that if duplicates were detected for the create payload, the caller supplied the
     * override flag and holds the {@code PERSON_DUPLICATE_OVERRIDE} authority.
     */
    public void assertOverrideAllowedIfDuplicates(
            DuplicateCheckResponse matches,
            boolean overrideRequested,
            boolean hasOverrideAuthority) {
        if (matches.matches().isEmpty()) {
            return;
        }
        if (!overrideRequested) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Possible duplicates detected — resubmit with overrideDuplicates=true"
                            + " to force creation");
        }
        if (!hasOverrideAuthority) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Overriding duplicates requires PERSON_DUPLICATE_OVERRIDE");
        }
    }
}
