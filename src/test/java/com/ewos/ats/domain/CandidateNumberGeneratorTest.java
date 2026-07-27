package com.ewos.ats.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ewos.ats.infrastructure.persistence.CandidateRepository;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CandidateNumberGeneratorTest {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^CAND-\\d{6}-\\d{6}$");

    @Test
    void generatesANumberMatchingTheExpectedShape() {
        CandidateRepository repo = mock(CandidateRepository.class);
        when(repo.existsByTenantIdAndCompanyIdAndCandidateNumberIgnoreCase(any(), any(), any()))
                .thenReturn(false);
        CandidateNumberGenerator generator = new CandidateNumberGenerator(repo);

        String number = generator.generate(UUID.randomUUID(), UUID.randomUUID());

        assertThat(number).matches(NUMBER_PATTERN);
    }

    @Test
    void retriesOnCollisionUntilAFreeNumberIsFound() {
        CandidateRepository repo = mock(CandidateRepository.class);
        when(repo.existsByTenantIdAndCompanyIdAndCandidateNumberIgnoreCase(any(), any(), any()))
                .thenReturn(true, true, false);
        CandidateNumberGenerator generator = new CandidateNumberGenerator(repo);

        String number = generator.generate(UUID.randomUUID(), UUID.randomUUID());

        assertThat(number).matches(NUMBER_PATTERN);
    }

    @Test
    void fallsBackToARandomSuffixWhenEveryAttemptCollides() {
        CandidateRepository repo = mock(CandidateRepository.class);
        when(repo.existsByTenantIdAndCompanyIdAndCandidateNumberIgnoreCase(any(), any(), any()))
                .thenReturn(true);
        CandidateNumberGenerator generator = new CandidateNumberGenerator(repo);

        String number = generator.generate(UUID.randomUUID(), UUID.randomUUID());

        assertThat(number).startsWith("CAND-").doesNotMatch(NUMBER_PATTERN);
    }

    // Regression test for a real production bug: a second, package-private constructor
    // (never called anywhere, added for a Clock override that no test ever used) made
    // Spring unable to determine which constructor to autowire, since neither was
    // annotated @Autowired and there was no longer a single unambiguous one. Every
    // Spring context that scanned com.ewos.ats failed to boot with "No default
    // constructor found" — invisible locally because AbstractIntegrationTest's
    // Testcontainers static initializer fails first in any environment without
    // Docker (this sandbox included), masking the real error.
    @Test
    void hasExactlyOneConstructorSoSpringCanAutowireItUnambiguously() {
        assertThat(CandidateNumberGenerator.class.getDeclaredConstructors()).hasSize(1);
    }
}
