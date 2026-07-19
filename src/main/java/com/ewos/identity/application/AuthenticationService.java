package com.ewos.identity.application;

import com.ewos.common.exception.ApiException;
import com.ewos.identity.api.dto.TokenResponse;
import com.ewos.identity.domain.LoginEventType;
import com.ewos.identity.domain.Permission;
import com.ewos.identity.domain.RefreshToken;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.RefreshTokenRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.security.jwt.JwtProperties;
import com.ewos.security.jwt.JwtService;
import com.ewos.security.ratelimit.AccountLockoutService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthenticationService {

    private static final int REFRESH_TOKEN_BYTES = 48;
    private static final String INVALID_CREDENTIALS = "Invalid username or password";
    private static final String UNKNOWN_ATTEMPT = "-";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final LoginHistoryRecorder loginHistoryRecorder;
    private final AccountLockoutService accountLockoutService;
    private final SecureRandom secureRandom = new SecureRandom();

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public AuthenticationService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            LoginHistoryRecorder loginHistoryRecorder,
            AccountLockoutService accountLockoutService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.loginHistoryRecorder = loginHistoryRecorder;
        this.accountLockoutService = accountLockoutService;
    }

    public TokenResponse login(
            String username, String rawPassword, String ipAddress, String userAgent) {
        Optional<User> maybeUser = userRepository.findByUsername(username);
        if (maybeUser.isEmpty()) {
            loginHistoryRecorder.record(
                    LoginEventType.LOGIN_FAILURE,
                    null,
                    username,
                    ipAddress,
                    userAgent,
                    "unknown user");
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }

        User user = maybeUser.get();

        // Time-based lockout must be checked before we spend cycles on bcrypt — an already-locked
        // account should short-circuit.
        try {
            accountLockoutService.assertNotLocked(user);
        } catch (ApiException locked) {
            loginHistoryRecorder.record(
                    LoginEventType.LOGIN_FAILURE,
                    user,
                    username,
                    ipAddress,
                    userAgent,
                    "account temporarily locked");
            throw locked;
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            boolean nowLocked = accountLockoutService.recordFailedAttempt(user);
            loginHistoryRecorder.record(
                    LoginEventType.LOGIN_FAILURE,
                    user,
                    username,
                    ipAddress,
                    userAgent,
                    nowLocked ? "invalid password — account now locked" : "invalid password");
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            loginHistoryRecorder.record(
                    LoginEventType.LOGIN_FAILURE,
                    user,
                    username,
                    ipAddress,
                    userAgent,
                    "account disabled or locked");
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is disabled or locked");
        }

        accountLockoutService.recordSuccessfulLogin(user);
        user.setLastLoginAt(Instant.now());
        loginHistoryRecorder.record(
                LoginEventType.LOGIN_SUCCESS, user, username, ipAddress, userAgent, null);
        // Fresh login → new family id. Device claims (WP later) populate the label.
        return issueTokens(user, null, null);
    }

    public TokenResponse refresh(String presentedRefreshToken, String ipAddress, String userAgent) {
        String hash = sha256Hex(presentedRefreshToken);
        Optional<RefreshToken> maybeStored = refreshTokenRepository.findByTokenHash(hash);

        if (maybeStored.isEmpty()) {
            loginHistoryRecorder.record(
                    LoginEventType.REFRESH_FAILURE,
                    null,
                    UNKNOWN_ATTEMPT,
                    ipAddress,
                    userAgent,
                    "unknown refresh token");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        RefreshToken stored = maybeStored.get();
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            loginHistoryRecorder.record(
                    LoginEventType.REFRESH_FAILURE,
                    stored.getUser(),
                    stored.getUser().getUsername(),
                    ipAddress,
                    userAgent,
                    stored.isRevoked() ? "revoked" : "expired");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }

        stored.setRevoked(true);
        stored.setRevokedReason("rotated");
        User user = stored.getUser();
        // Carry the family id forward so device-based revocation (later WP) can still act on it.
        UUID rotatingFamily = stored.getFamilyId();
        loginHistoryRecorder.record(
                LoginEventType.REFRESH_SUCCESS,
                user,
                user.getUsername(),
                ipAddress,
                userAgent,
                null);
        return issueTokens(user, rotatingFamily, stored.getDeviceLabel());
    }

    /**
     * Idempotently revokes the presented refresh token. Unknown or already-revoked tokens don't
     * error — logout should always succeed from the client's view.
     */
    public void logout(String presentedRefreshToken, String ipAddress, String userAgent) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            return;
        }
        String hash = sha256Hex(presentedRefreshToken);
        refreshTokenRepository
                .findByTokenHash(hash)
                .ifPresent(
                        rt -> {
                            if (!rt.isRevoked()) {
                                rt.setRevoked(true);
                                rt.setRevokedReason("logout");
                            }
                            User user = rt.getUser();
                            loginHistoryRecorder.record(
                                    LoginEventType.LOGOUT,
                                    user,
                                    user.getUsername(),
                                    ipAddress,
                                    userAgent,
                                    null);
                        });
    }

    private TokenResponse issueTokens(User user, UUID inheritedFamilyId, String deviceLabel) {
        List<String> authorities = collectAuthorities(user);

        String accessToken =
                jwtService.generateAccessToken(
                        user.getId().toString(),
                        Map.of("authorities", authorities, "username", user.getUsername()));

        String refreshValue = generateOpaqueToken();
        RefreshToken rt = new RefreshToken();
        rt.setTokenHash(sha256Hex(refreshValue));
        rt.setUser(user);
        rt.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()));
        // If we're rotating an existing family, carry the id forward; otherwise a new family
        // starts, with id == this token's id. The V7 migration back-fills the same shape for
        // legacy rows.
        rt.setFamilyId(inheritedFamilyId != null ? inheritedFamilyId : UUID.randomUUID());
        rt.setDeviceLabel(deviceLabel);
        refreshTokenRepository.save(rt);

        return new TokenResponse(
                accessToken, refreshValue, "Bearer", jwtProperties.accessTokenTtl().toSeconds());
    }

    private static List<String> collectAuthorities(User user) {
        return user.getRoles().stream()
                .flatMap(
                        role ->
                                Stream.concat(
                                        Stream.of("ROLE_" + role.getName()),
                                        role.getPermissions().stream().map(Permission::getCode)))
                .distinct()
                .toList();
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }
}
