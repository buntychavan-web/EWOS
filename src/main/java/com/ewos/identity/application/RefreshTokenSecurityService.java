package com.ewos.identity.application;

import com.ewos.identity.infrastructure.persistence.RefreshTokenRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes an entire refresh-token family in its own {@code REQUIRES_NEW} transaction — the same
 * pattern {@link LoginHistoryRecorder#record} and {@code AccountLockoutService} use, and for the
 * same reason: {@link AuthenticationService#refresh} always throws right after calling this, and
 * the default rollback rule for that unchecked {@code ApiException} would otherwise take the
 * revocation down with it.
 */
@Service
public class RefreshTokenSecurityService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenSecurityService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Revokes every non-revoked token in {@code familyId}. Called when a refresh token that was
     * already revoked (typically because it was already rotated) is presented again — the standard
     * signal that a stolen refresh token is being replayed after the legitimate client already
     * moved past it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamilyDurably(UUID familyId, String reason) {
        refreshTokenRepository.revokeFamily(familyId, reason);
    }
}
