package com.ewos.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditorProviderTest {

    private final AuditorProvider provider = new AuditorProvider();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void emptyWhenNoSecurityContext() {
        assertThat(provider.getCurrentAuditor()).isEmpty();
    }

    @Test
    void emptyForAnonymousToken() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new AnonymousAuthenticationToken(
                                "key",
                                "anonymousUser",
                                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(provider.getCurrentAuditor()).isEmpty();
    }

    @Test
    void emptyForNonUuidSubject() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "svc:reporter",
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));

        assertThat(provider.getCurrentAuditor()).isEmpty();
    }

    @Test
    void returnsUuidForAuthenticatedRequest() {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                id.toString(),
                                "n/a",
                                List.of(new SimpleGrantedAuthority("USER_READ"))));

        assertThat(provider.getCurrentAuditor()).contains(id);
    }
}
