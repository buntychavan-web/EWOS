package com.ewos.exit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sprint 26A P0-2: an authenticated principal whose name isn't a valid UUID must fail loudly, not
 * silently disappear into a null actor that leaves audit fields (submittedBy, acceptedBy, ...)
 * looking unattributed. The genuinely-unauthenticated case is unaffected — that's a legitimate "no
 * actor" case relied on by system-initiated actions.
 */
@ExtendWith(MockitoExtension.class)
class ExitSecurityTest {

    @Mock Authentication authentication;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentActorReturnsNullWhenThereIsNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThat(ExitSecurity.currentActor()).isNull();
    }

    @Test
    void currentActorReturnsNullWhenThePrincipalNameIsNull() {
        when(authentication.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(ExitSecurity.currentActor()).isNull();
    }

    @Test
    void currentActorParsesAValidUuidPrincipalName() {
        UUID actor = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                actor.toString(), "n/a", List.of()));

        assertThat(ExitSecurity.currentActor()).isEqualTo(actor);
    }

    @Test
    void currentActorFailsLoudlyOnAnInvalidUuidPrincipalNameRatherThanReturningNull() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("not-a-uuid", "n/a", List.of()));

        assertThatThrownBy(ExitSecurity::currentActor)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not-a-uuid");
    }

    @Test
    void currentActorFailsLoudlyWhenAnAuthenticatedTokenHasNoUsablePrincipalName() {
        // Authentication#getName() returns "" (not null) for a token with a null principal — this
        // is the realistic shape the "malformed name" case takes with real Spring Security tokens.
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(null, "n/a", List.of()));

        assertThatThrownBy(ExitSecurity::currentActor).isInstanceOf(IllegalStateException.class);
    }
}
