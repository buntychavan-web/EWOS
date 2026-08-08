package com.ewos.shared.idempotency;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Sprint 27B — one claimed {@code Idempotency-Key} for a state-changing POST. See {@link
 * IdempotencyService} for the claim/replay protocol this row participates in.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "endpoint", nullable = false, length = 200)
    private String endpoint;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKeyValue;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getIdempotencyKeyValue() {
        return idempotencyKeyValue;
    }

    public void setIdempotencyKeyValue(String idempotencyKeyValue) {
        this.idempotencyKeyValue = idempotencyKeyValue;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }
}
