package com.ewos.notification.infrastructure.persistence;

import com.ewos.notification.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndTenantIdAndRecipientActorId(
            UUID id, UUID tenantId, UUID recipientActorId);

    /** Sprint 27C — dismissed (soft-deleted) rows must never appear in {@code mine()}. */
    @Query(
            "select n from Notification n where n.tenantId = :tenantId and n.recipientActorId ="
                    + " :recipientActorId and n.deletedAt is null order by n.createdAt desc")
    Page<Notification> findAllByTenantIdAndRecipientActorIdOrderByCreatedAtDesc(
            @Param("tenantId") UUID tenantId,
            @Param("recipientActorId") UUID recipientActorId,
            Pageable pageable);

    /** Sprint 27C — dismissed rows must never count toward the unread badge either. */
    @Query(
            "select count(n) from Notification n where n.tenantId = :tenantId and"
                    + " n.recipientActorId = :recipientActorId and n.readAt is null and"
                    + " n.deletedAt is null")
    long countByTenantIdAndRecipientActorIdAndReadAtIsNull(
            @Param("tenantId") UUID tenantId, @Param("recipientActorId") UUID recipientActorId);

    @Modifying
    @Query(
            "update Notification n set n.readAt = CURRENT_TIMESTAMP where n.id = :id and"
                    + " n.tenantId = :tenantId and n.recipientActorId = :recipientActorId and"
                    + " n.readAt is null and n.deletedAt is null")
    int markRead(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("recipientActorId") UUID recipientActorId);

    /**
     * Sprint 27C — soft-delete (dismiss) a notification, ownership-checked in the same statement.
     * Idempotent by construction: re-dismissing an already-deleted row matches zero rows (the
     * {@code deleted_at is null} predicate), not an error — {@code NotificationService.dismiss}
     * treats "0 rows updated but the row exists and belongs to the caller" as success.
     */
    @Modifying
    @Query(
            "update Notification n set n.deletedAt = CURRENT_TIMESTAMP where n.id = :id and"
                    + " n.tenantId = :tenantId and n.recipientActorId = :recipientActorId and"
                    + " n.deletedAt is null")
    int dismiss(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("recipientActorId") UUID recipientActorId);

    /**
     * Sprint 27C — keyset/cursor page for the unified Notification Inbox (PRD §4.6). {@code
     * cursorCreatedAt}/{@code cursorId} are both {@code null} for the first page; a caller passes
     * back the last item's {@code createdAt}/{@code id} to fetch the next page, ordered newest
     * first with {@code id} as a stable tiebreaker for rows sharing a timestamp.
     *
     * <p>Sprint 27C fix-round (F5 CI fix) — {@code cast(:cursorCreatedAt as timestamp) is null}
     * rather than a bare {@code :cursorCreatedAt is null}: the real-database integration tests
     * added for F5 caught PostgreSQL rejecting the first-page call (where {@code cursorCreatedAt}
     * is {@code null}) with {@code "could not determine data type of parameter"} — a bare parameter
     * used only in an {@code IS NULL} check, with no other typed context at that occurrence, gives
     * Postgres nothing to infer its type from. This was a genuine pre-existing bug: {@code
     * NotificationService.myPage} calls this with a null cursor on every first page load, so every
     * unfiltered notification-inbox request would have failed against a real database. The explicit
     * cast only affects the nullity check; the two other occurrences of {@code :cursorCreatedAt}
     * (the actual {@code <} / {@code =} comparisons) are untouched and keep inferring their type
     * from the {@code n.createdAt} column as before.
     */
    @Query(
            "select n from Notification n where n.tenantId = :tenantId and n.recipientActorId ="
                    + " :recipientActorId and n.deletedAt is null and (:unreadOnly = false or"
                    + " n.readAt is null) and (cast(:cursorCreatedAt as timestamp) is null or"
                    + " n.createdAt < :cursorCreatedAt or (n.createdAt = :cursorCreatedAt and"
                    + " n.id < :cursorId)) order by n.createdAt desc, n.id desc")
    List<Notification> findPage(
            @Param("tenantId") UUID tenantId,
            @Param("recipientActorId") UUID recipientActorId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
