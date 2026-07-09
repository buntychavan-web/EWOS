package com.ewos.identity.application;

import com.ewos.common.exception.ApiException;
import com.ewos.identity.api.dto.TokenResponse;
import com.ewos.identity.domain.Permission;
import com.ewos.identity.domain.RefreshToken;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.RefreshTokenRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.security.jwt.JwtProperties;
import com.ewos.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String IP = "10.0.0.1";
    private static final String UA = "junit";

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock LoginHistoryRecorder loginHistoryRecorder;

    private final JwtProperties jwtProperties = new JwtProperties(
            "unit-test-secret-key-that-is-definitely-long-enough-for-hs256-signing",
            "ewos-test", Duration.ofMinutes(15), Duration.ofDays(7));

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(userRepository, refreshTokenRepository,
                passwordEncoder, jwtService, jwtProperties, loginHistoryRecorder);
        lenient().when(jwtService.generateAccessToken(any(), any())).thenReturn("stub-jwt");
    }

    @Test
    void loginIssuesTokensWhenCredentialsMatch() {
        User user = adminUser();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", user.getPasswordHash())).thenReturn(true);

        TokenResponse response = service.login("admin", "pw", IP, UA);

        assertThat(response.accessToken()).isEqualTo("stub-jwt");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(Duration.ofMinutes(15).toSeconds());
        assertThat(user.getLastLoginAt()).isNotNull();

        verify(loginHistoryRecorder).record(eq(user), eq("admin"), eq(IP), eq(UA), eq(true), isNull());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(response.refreshToken()));
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void loginProjectsRolesAndPermissionsIntoAuthoritiesClaim() {
        User user = adminUser();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", user.getPasswordHash())).thenReturn(true);

        service.login("admin", "pw", IP, UA);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jwtService).generateAccessToken(any(), claimsCaptor.capture());
        Object authorities = claimsCaptor.getValue().get("authorities");
        assertThat(authorities).asList().contains("ROLE_SYSTEM_ADMIN", "USER_READ");
    }

    @Test
    void loginRecordsUnknownUserFailure() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ghost", "pw", IP, UA))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(loginHistoryRecorder).record(isNull(), eq("ghost"), eq(IP), eq(UA), eq(false), eq("unknown user"));
    }

    @Test
    void loginRecordsBadPasswordFailure() {
        User user = adminUser();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> service.login("admin", "bad", IP, UA))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(loginHistoryRecorder).record(eq(user), eq("admin"), eq(IP), eq(UA), eq(false), eq("invalid password"));
    }

    @Test
    void loginRecordsDisabledAccountFailure() {
        User user = adminUser();
        user.setEnabled(false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> service.login("admin", "pw", IP, UA))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);

        verify(loginHistoryRecorder).record(eq(user), eq("admin"), eq(IP), eq(UA), eq(false),
                eq("account disabled or locked"));
    }

    @Test
    void refreshRotatesTokenAndRevokesOldOne() {
        User user = adminUser();
        String presented = "known-refresh-value";
        RefreshToken stored = new RefreshToken();
        stored.setTokenHash(sha256Hex(presented));
        stored.setUser(user);
        stored.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));

        when(refreshTokenRepository.findByTokenHash(sha256Hex(presented))).thenReturn(Optional.of(stored));

        TokenResponse response = service.refresh(presented);

        assertThat(stored.isRevoked()).isTrue();
        assertThat(response.refreshToken()).isNotEqualTo(presented);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(sha256Hex(response.refreshToken()));
    }

    @Test
    void refreshRejectsExpiredToken() {
        String presented = "expired-token";
        RefreshToken stored = new RefreshToken();
        stored.setTokenHash(sha256Hex(presented));
        stored.setUser(adminUser());
        stored.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));

        when(refreshTokenRepository.findByTokenHash(sha256Hex(presented))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refresh(presented))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshRejectsRevokedToken() {
        String presented = "revoked-token";
        RefreshToken stored = new RefreshToken();
        stored.setTokenHash(sha256Hex(presented));
        stored.setUser(adminUser());
        stored.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        stored.setRevoked(true);

        when(refreshTokenRepository.findByTokenHash(sha256Hex(presented))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refresh(presented))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("unknown"))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static User adminUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("admin");
        user.setEmail("admin@ewos.local");
        user.setPasswordHash("bcrypt-hash");
        user.setEnabled(true);
        user.setAccountNonLocked(true);

        Permission perm = new Permission("USER_READ", "read");
        perm.setId(UUID.randomUUID());
        Role role = new Role("SYSTEM_ADMIN", "admin");
        role.setId(UUID.randomUUID());
        role.setPermissions(new HashSet<>(Set.of(perm)));

        user.setRoles(new HashSet<>(Set.of(role)));
        return user;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
