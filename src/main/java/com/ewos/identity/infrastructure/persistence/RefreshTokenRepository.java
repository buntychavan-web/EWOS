package com.ewos.identity.infrastructure.persistence;

import com.ewos.identity.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteAllExpired(@Param("now") Instant now);

    @Modifying
    @Query(
            "UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedReason = :reason"
                    + " WHERE rt.familyId = :familyId AND rt.revoked = false")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("reason") String reason);

    @Modifying
    @Query(
            "UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedReason = :reason"
                    + " WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("reason") String reason);
}
