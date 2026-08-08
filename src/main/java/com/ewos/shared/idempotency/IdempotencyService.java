package com.ewos.shared.idempotency;

import com.ewos.shared.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sprint 27B — {@code Idempotency-Key} support for the unified approvals inbox's state-changing
 * POST endpoints (PRD §16). No repository-level {@code @Transactional} wraps {@link #execute}
 * itself on purpose: each step below (claim insert, the caller's action, the response-body update)
 * runs in its own Spring Data-managed transaction, so a unique-constraint violation on the claim
 * insert never poisons a later step — see the claim/replay protocol below.
 *
 * <p>Protocol: first caller with a given {@code (tenant, actor, endpoint, key)} tuple inserts a
 * claim row and wins the race (enforced by the table's unique constraint); it then performs the
 * action and stores either the JSON response or, if the action throws, a failure marker on that
 * same row. A second caller with the same tuple loses the insert, and either replays the first
 * caller's stored outcome — success response or the original failure, re-thrown as-is — or gets a
 * {@code 409} only while the first call is still genuinely in flight (no outcome recorded yet). A
 * caller that starts a fresh action after a crash mid-claim (row inserted, neither outcome ever
 * stored) is a known limitation — see the PR description.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public <T> T execute(
            UUID tenantId,
            UUID actorUserId,
            String endpoint,
            String idempotencyKey,
            Class<T> responseType,
            Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        IdempotencyKey claim = new IdempotencyKey();
        claim.setTenantId(tenantId);
        claim.setActorUserId(actorUserId);
        claim.setEndpoint(endpoint);
        claim.setIdempotencyKeyValue(idempotencyKey);
        try {
            claim = repository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException alreadyClaimed) {
            return replayOrConflict(tenantId, actorUserId, endpoint, idempotencyKey, responseType);
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException actionFailed) {
            claim.setFailed(true);
            claim.setFailureStatus(statusOf(actionFailed));
            claim.setFailureMessage(actionFailed.getMessage());
            repository.save(claim);
            throw actionFailed;
        }
        claim.setResponseBody(writeJson(result));
        repository.save(claim);
        return result;
    }

    private <T> T replayOrConflict(
            UUID tenantId,
            UUID actorUserId,
            String endpoint,
            String idempotencyKey,
            Class<T> responseType) {
        IdempotencyKey existing =
                repository
                        .findByTenantIdAndActorUserIdAndEndpointAndIdempotencyKeyValue(
                                tenantId, actorUserId, endpoint, idempotencyKey)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.CONFLICT,
                                                "A request with this Idempotency-Key is already"
                                                        + " being processed"));
        if (existing.isFailed()) {
            HttpStatus status =
                    HttpStatus.resolve(
                            existing.getFailureStatus() == null
                                    ? HttpStatus.INTERNAL_SERVER_ERROR.value()
                                    : existing.getFailureStatus());
            throw new ApiException(
                    status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status,
                    existing.getFailureMessage());
        }
        if (existing.getResponseBody() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "A request with this Idempotency-Key is already being processed");
        }
        return readJson(existing.getResponseBody(), responseType);
    }

    private static Integer statusOf(RuntimeException e) {
        return e instanceof ApiException apiException
                ? apiException.getStatus().value()
                : HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to record idempotent response", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to replay idempotent response", e);
        }
    }
}
