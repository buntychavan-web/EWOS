package com.ewos.ats.domain;

import com.ewos.ats.infrastructure.persistence.CandidateRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Generates a candidate number of the form {@code CAND-YYYYMM-XXXXXX} where the last block is a
 * zero-padded random six-digit token. Collisions are retried up to a small bound; unicity is
 * enforced by the partial-unique index on {@code candidates.candidate_number}.
 */
@Component
public class CandidateNumberGenerator {

    private static final int MAX_ATTEMPTS = 8;

    private final CandidateRepository candidates;
    private final Clock clock;

    public CandidateNumberGenerator(CandidateRepository candidates) {
        this(candidates, Clock.systemUTC());
    }

    CandidateNumberGenerator(CandidateRepository candidates, Clock clock) {
        this.candidates = candidates;
        this.clock = clock;
    }

    public String generate(UUID tenantId, UUID companyId) {
        LocalDate today = LocalDate.now(clock);
        String prefix = String.format("CAND-%04d%02d-", today.getYear(), today.getMonthValue());
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            int token = ThreadLocalRandom.current().nextInt(0, 1_000_000);
            String candidate = prefix + String.format("%06d", token);
            if (!candidates.existsByTenantIdAndCompanyIdAndCandidateNumberIgnoreCase(
                    tenantId, companyId, candidate)) {
                return candidate;
            }
        }
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
