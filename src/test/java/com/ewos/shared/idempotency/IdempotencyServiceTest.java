package com.ewos.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/** Sprint 27B — claim/replay protocol behind the approvals inbox's {@code Idempotency-Key}. */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock IdempotencyKeyRepository repository;

    private IdempotencyService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository, new ObjectMapper());
    }

    @Test
    void executesActionDirectlyWhenNoIdempotencyKeyIsSupplied() {
        AtomicInteger calls = new AtomicInteger();
        String result =
                service.execute(
                        tenantId,
                        actorId,
                        "ep",
                        null,
                        String.class,
                        () -> {
                            calls.incrementAndGet();
                            return "done";
                        });

        assertThat(result).isEqualTo("done");
        assertThat(calls.get()).isEqualTo(1);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void firstCallClaimsTheKeyExecutesOnceAndStoresTheResponse() {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        AtomicInteger calls = new AtomicInteger();

        String result =
                service.execute(
                        tenantId,
                        actorId,
                        "ep",
                        "key-1",
                        String.class,
                        () -> {
                            calls.incrementAndGet();
                            return "created";
                        });

        assertThat(result).isEqualTo("created");
        assertThat(calls.get()).isEqualTo(1);
        verify(repository, times(1)).save(any());
    }

    @Test
    void secondCallWithTheSameKeyReplaysTheStoredResponseWithoutReRunningTheAction() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        IdempotencyKey existing = new IdempotencyKey();
        existing.setResponseBody("\"cached-response\"");
        when(repository.findByTenantIdAndActorUserIdAndEndpointAndIdempotencyKeyValue(
                        tenantId, actorId, "ep", "key-1"))
                .thenReturn(Optional.of(existing));
        AtomicInteger calls = new AtomicInteger();

        String result =
                service.execute(
                        tenantId,
                        actorId,
                        "ep",
                        "key-1",
                        String.class,
                        () -> {
                            calls.incrementAndGet();
                            return "should-not-run";
                        });

        assertThat(result).isEqualTo("cached-response");
        assertThat(calls.get()).isZero();
    }

    @Test
    void firstCallPersistsAFailureMarkerAndRethrowsWhenTheActionThrows() {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        // Deliberately NOT 409, so the later replay test can prove it returns the *original*
        // status rather than the generic "still in flight" 409 the conflict path also uses.
        ApiException failure = new ApiException(HttpStatus.NOT_FOUND, "Timesheet not found");

        assertThatThrownBy(
                        () ->
                                service.execute(
                                        tenantId,
                                        actorId,
                                        "ep",
                                        "key-1",
                                        String.class,
                                        () -> {
                                            throw failure;
                                        }))
                .isSameAs(failure);

        org.mockito.ArgumentCaptor<IdempotencyKey> captor =
                org.mockito.ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().isFailed()).isTrue();
        assertThat(captor.getValue().getFailureStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(captor.getValue().getFailureMessage()).isEqualTo("Timesheet not found");
        assertThat(captor.getValue().getResponseBody()).isNull();
    }

    @Test
    void retryOfTheSameKeyAfterAFailureRethrowsTheOriginalFailureWithoutReRunningTheAction() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        IdempotencyKey failed = new IdempotencyKey();
        failed.setFailed(true);
        failed.setFailureStatus(HttpStatus.NOT_FOUND.value());
        failed.setFailureMessage("Timesheet not found");
        when(repository.findByTenantIdAndActorUserIdAndEndpointAndIdempotencyKeyValue(
                        tenantId, actorId, "ep", "key-1"))
                .thenReturn(Optional.of(failed));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                service.execute(
                                        tenantId,
                                        actorId,
                                        "ep",
                                        "key-1",
                                        String.class,
                                        () -> {
                                            calls.incrementAndGet();
                                            return "should-not-run";
                                        }))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                // Proves this is the replayed original failure (404), NOT the generic
                // still-in-flight 409 the conflict path below returns.
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(calls.get()).isZero();
    }

    @Test
    void concurrentDuplicateInFlightReturnsConflictInsteadOfReplayingAnEmptyResponse() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        IdempotencyKey inFlight = new IdempotencyKey();
        inFlight.setResponseBody(null);
        when(repository.findByTenantIdAndActorUserIdAndEndpointAndIdempotencyKeyValue(
                        tenantId, actorId, "ep", "key-1"))
                .thenReturn(Optional.of(inFlight));

        assertThatThrownBy(
                        () ->
                                service.execute(
                                        tenantId,
                                        actorId,
                                        "ep",
                                        "key-1",
                                        String.class,
                                        () -> "unused"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
