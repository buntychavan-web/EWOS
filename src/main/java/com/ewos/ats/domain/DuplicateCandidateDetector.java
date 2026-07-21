package com.ewos.ats.domain;

import com.ewos.ats.infrastructure.persistence.CandidateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Surfaces potentially-duplicate candidates for a new intake. Matches on:
 *
 * <ul>
 *   <li>Exact case-insensitive email
 *   <li>Exact match on {@link PhoneNormalizer#digitsOnly(String) normalized phone digits}
 * </ul>
 *
 * The detector never blocks — it reports hits so the caller (usually a workflow) can decide.
 */
@Component
public class DuplicateCandidateDetector {

    private final CandidateRepository candidates;

    public DuplicateCandidateDetector(CandidateRepository candidates) {
        this.candidates = candidates;
    }

    public List<DuplicateCandidateMatch> findDuplicates(UUID tenantId, String email, String phone) {
        List<DuplicateCandidateMatch> hits = new ArrayList<>();
        if (email != null && !email.isBlank()) {
            candidates
                    .findByTenantIdAndEmailIgnoreCase(tenantId, email)
                    .ifPresent(
                            c ->
                                    hits.add(
                                            new DuplicateCandidateMatch(
                                                    c.getId(),
                                                    c.getCandidateNumber(),
                                                    c.fullName(),
                                                    DuplicateMatchType.EMAIL_EXACT)));
        }
        String digits = PhoneNormalizer.digitsOnly(phone);
        if (digits != null) {
            for (Candidate c : candidates.findAllByTenantIdAndPhoneDigits(tenantId, digits)) {
                if (hits.stream().noneMatch(h -> h.candidateId().equals(c.getId()))) {
                    hits.add(
                            new DuplicateCandidateMatch(
                                    c.getId(),
                                    c.getCandidateNumber(),
                                    c.fullName(),
                                    DuplicateMatchType.PHONE_EXACT));
                }
            }
        }
        return hits;
    }
}
