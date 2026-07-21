# WP-001 — Platform Foundation — Final Delivery for CTO Approval

- **Date:** 2026-07-19
- **Work package:** WP-001 — Platform Foundation (Final)
- **Branch:** `claude/wp-001-final`
- **Prior verdict:** APPROVED WITH CONDITIONS (CTO, 2026-07-14)
- **Not for merge until:** CTO Final Approval

---

## 0. Scope of this delivery

This addresses **every item** on the CTO's conditional-approval punch list. Nothing else is in
scope — WP-002 is untouched, no business modules were added.

---

## 1. Punch-list compliance

| # | CTO condition | Status | Evidence |
|---|---|---|---|
| 1 | Merge PR #4 (CI Testcontainers) | ✅ Merged to `main` | commit `7652fb2` |
| 2 | Merge PR #5 (Engineering Documentation) | ✅ Merged to `main` | commit `3d0c2b4` |
| 3 | Merge PR #7 (Production CORS) — part of WP-001 | ✅ **Rebuilt and pending in this PR** — see §2 | `src/main/java/com/ewos/security/cors/*`, `docs/adr/0004-cors-configuration.md` |
| 4 | Remove JWT_SECRET placeholder — refuse to start outside dev/test | ✅ Delivered | `com.ewos.security.jwt.JwtSecretGuard` + `JwtSecretGuardTest` (7 tests) |
| 5 | Rate limiting + account lockout for auth | ✅ Delivered | `com.ewos.security.ratelimit.*` + V6 migration + `AccountLockoutServiceTest` (5 tests) + `InMemoryRateLimiterTest` (4 tests) |
| 6 | Document Actuator endpoint security | ✅ Delivered | `docs/operations/auditor-and-actuator.md` §2 |
| 7 | Design refresh-token revocation for device-based sessions | ✅ Delivered (schema + hooks; feature dormant) | V7 migration + `RefreshToken.familyId/deviceLabel/revokedReason` + `RefreshTokenRepository.revokeFamily` / `revokeAllForUser` |
| 8 | Scheduled purge framework (disabled initially) | ✅ Delivered | `com.ewos.common.purge.PurgeJob` + `PurgeProperties`, all defaults OFF |
| 9 | Document AuditorProvider behavior when no authenticated user exists | ✅ Delivered | JavaDoc on `AuditorProvider` + `docs/operations/auditor-and-actuator.md` §1 |

**Zero conditions outstanding.**

---

## 2. Note on the PR #7 rebuild

The CTO said "MERGE PR #7". As-authored, PR #7's head branch `claude/cors-configuration` was
stacked on `claude/repository-selection-575dn9`, which carries the un-approved Sprints 6, 7, 8.1.
Merging that PR into `main` would drag business modules across the WP-001 boundary — which the
CTO explicitly said not to do (PR #6 → HOLD).

**Resolution:** the CORS surface has been rebuilt on `claude/wp-001-final` (this branch) via a
`git checkout <cors-commit> -- <cors-paths-only>` operation. The result is a clean CORS delivery
with **zero Sprint 6/7/8.1 leakage**. The old PR #7 will be closed with a pointer to this new PR
once approval lands. ADR-0004 (CORS) is included verbatim.

---

## 3. What changed since the conditional approval

### 3.1 JWT_SECRET production guard — `JwtSecretGuard`

- Refuses to boot in any profile other than `dev` / `test` / (unset default) when:
  - The secret matches any of six placeholder markers (`change-me`, `changeme`, `do-not-use`, `donotuse`, `placeholder`, `example`, `sample`, case-insensitive), OR
  - The secret is null / shorter than 256 bits (32 bytes, HS256 minimum).
- Non-prod placeholder use logs a WARN instead of throwing — so dev + CI keep working.
- 7 unit tests cover: prod + placeholder → throw; prod + test secret → throw; prod + too-short → throw; staging + placeholder → throw; dev + placeholder → allow; test + test secret → allow; strong secret + any profile → allow.

### 3.2 Account lockout — `AccountLockoutService`

- New columns on `users` via V6 migration: `failed_login_attempts INT NOT NULL DEFAULT 0`, `locked_until TIMESTAMPTZ`.
- Configurable via `app.security.lockout.*`:
  - `enabled` (default true)
  - `max-attempts` (default 5)
  - `lock-duration` (default 15m)
- Contract:
  - Failed password → `failed_login_attempts++`; at threshold, `locked_until = now + lockDuration`.
  - Login attempt while `locked_until` in the future → `423 Locked`, audited as `LOGIN_FAILURE` with reason `account temporarily locked`.
  - Expired `locked_until` → auto-cleared on the next attempt; counter resets.
  - Successful login → counter + `locked_until` cleared.
- Wired into `AuthenticationService.login` — check runs *before* bcrypt to avoid wasting CPU on a locked account.

### 3.3 Per-IP rate limiter — `AuthRateLimitFilter` + `InMemoryRateLimiter`

- Guards `POST /api/v1/auth/login` and `POST /api/v1/auth/refresh` on a per-`(path, ip)` basis.
- Sliding window: `ReentrantLock`-guarded deque per key; O(1) admit / reject; no external deps.
- Configurable via `app.security.rate-limit.*` (default: 60 attempts / 1 min / enabled).
- Trips → `429 Too Many Requests` with `ApiError` envelope and `Retry-After` header.
- `application-test.yml` disables it (MockMvc reuses `127.0.0.1` and would trip).
- Redis-backed replacement is a drop-in when we go multi-instance — the `allow` contract is stable.

### 3.4 Refresh-token device-family — `RefreshToken.familyId/deviceLabel/revokedReason`

- V7 migration adds `family_id UUID NOT NULL`, `device_label VARCHAR(255)`, `revoked_reason VARCHAR(60)`. Legacy rows back-filled with `family_id = id`.
- `RefreshTokenRepository.revokeFamily(familyId, reason)` and `.revokeAllForUser(userId, reason)` are ready.
- Auth service semantics: rotation carries the family id forward (rotate = same family); logout marks reason `logout`; refresh marks previous reason `rotated`.
- Device claims on JWTs will be populated in a later WP; the schema is now schema-safe for that.

### 3.5 Scheduled purge framework — `PurgeJob`

- Cron-driven (`@Scheduled` cron `${app.purge.cron:0 15 3 * * *}` in UTC).
- **Ships disabled** — `app.purge.enabled=false` and every sub-job flag defaults to false. Nothing is deleted until an operator turns each on.
- First job: `purgeExpiredRefreshTokens()` — deletes tokens whose `expires_at` is more than `expiredRefreshTokenRetention` (default 30 days) in the past.
- Second job (stub, disabled): `purgeSoftDeletedRows()` — pattern in place for future soft-delete retention decisions.
- `runNow()` public trigger for ops scripts and future integration tests.

### 3.6 Actuator security documentation — `docs/operations/auditor-and-actuator.md` §2

- Enumerates every public actuator path, per profile.
- Explicit "if you want to expose `/actuator/prometheus` in prod" runbook — three ordered steps, ADR requirement.
- Sensitive endpoints (`/env`, `/configprops`, `/beans`, `/mappings`, `/threaddump`, `/heapdump`, `/loggers`) called out with a hard "do not expose without ADR" rule.
- Includes a live-deployment verification runbook (three curls).

### 3.7 AuditorProvider — JavaDoc + `docs/operations/auditor-and-actuator.md` §1

- The three NULL-auditor situations enumerated (no context, anonymous, non-UUID subject).
- Rules for consumers: never assume non-null, never inner-join; include NULL rows as "system-initiated" in audit reports.
- Bootstrap-admin special case documented.
- Extension path documented for a future "system actor" placeholder identity.

---

## 4. Files delivered on this branch (vs. `main`)

### New — code
- `src/main/java/com/ewos/security/jwt/JwtSecretGuard.java`
- `src/main/java/com/ewos/security/cors/CorsProperties.java`
- `src/main/java/com/ewos/security/cors/CorsConfig.java`
- `src/main/java/com/ewos/security/ratelimit/AccountLockoutProperties.java`
- `src/main/java/com/ewos/security/ratelimit/AccountLockoutService.java`
- `src/main/java/com/ewos/security/ratelimit/RateLimitProperties.java`
- `src/main/java/com/ewos/security/ratelimit/InMemoryRateLimiter.java`
- `src/main/java/com/ewos/security/ratelimit/AuthRateLimitFilter.java`
- `src/main/java/com/ewos/common/purge/PurgeProperties.java`
- `src/main/java/com/ewos/common/purge/PurgeJob.java`

### Modified — code
- `src/main/java/com/ewos/EwosApplication.java` — `@EnableScheduling`.
- `src/main/java/com/ewos/security/SecurityConfig.java` — wires `CorsConfigurationSource`; drops unused `Customizer` import.
- `src/main/java/com/ewos/identity/domain/User.java` — `failedLoginAttempts`, `lockedUntil` fields.
- `src/main/java/com/ewos/identity/domain/RefreshToken.java` — `familyId`, `deviceLabel`, `revokedReason` fields.
- `src/main/java/com/ewos/identity/infrastructure/persistence/RefreshTokenRepository.java` — `revokeFamily`, `revokeAllForUser`.
- `src/main/java/com/ewos/identity/application/AuthenticationService.java` — lockout wiring, family-id propagation on rotation.
- `src/main/java/com/ewos/common/persistence/AuditorProvider.java` — expanded JavaDoc.

### New — migrations
- `V6__auth_hardening.sql` — `failed_login_attempts`, `locked_until` on `users`.
- `V7__refresh_token_families.sql` — `family_id`, `device_label`, `revoked_reason` on `refresh_tokens`.

### New — config
- `app.cors.*` in `application.yml` / per profile.
- `app.security.lockout.*`, `app.security.rate-limit.*` in `application.yml`.
- `app.purge.*` in `application.yml` (all defaults off).
- Test profile overrides for lockout / rate-limit (headroom high, IP-limit off).
- Prod profile: `APP_CORS_ALLOWED_ORIGINS` required, wildcards rejected at start-up.

### New — tests
- `src/test/java/com/ewos/security/jwt/JwtSecretGuardTest.java` (7 tests)
- `src/test/java/com/ewos/security/ratelimit/AccountLockoutServiceTest.java` (5 tests)
- `src/test/java/com/ewos/security/ratelimit/InMemoryRateLimiterTest.java` (4 tests)
- `src/test/java/com/ewos/security/cors/CorsConfigTest.java` (6 tests)
- `src/test/java/com/ewos/security/cors/CorsPreflightIntegrationTest.java` (3 tests, runs on CI)

### New — docs
- `docs/adr/0004-cors-configuration.md`
- `docs/operations/auditor-and-actuator.md`
- `docs/reviews/wp-001-final-cto-review.md` (this file)

---

## 5. Test results

**Local (this sandbox, `mvn test` excluding integration classes):**

| Suite | Tests | Status |
|---|---|---|
| `AuthenticationServiceTest` | 12 | ✅ |
| `UserServiceTest` | 12 | ✅ |
| `PasswordPolicyValidatorTest` | 7 | ✅ |
| `JwtServiceTest` | 2 | ✅ |
| `JwtSecretGuardTest` | 7 | ✅ (new) |
| `AccountLockoutServiceTest` | 5 | ✅ (new) |
| `InMemoryRateLimiterTest` | 4 | ✅ (new) |
| `CorsConfigTest` | 6 | ✅ (new) |
| **Total** | **55 / 55** | **green** |

**Integration tests:** `AuthControllerIntegrationTest`, `UserControllerIntegrationTest`,
`CorsPreflightIntegrationTest`, `EwosApplicationTests` — run on CI (require Docker). Sandbox has
none.

**Quality gates (2026-07-19, local):**
- Spotless — clean
- Checkstyle 10.18 — 0 violations
- PMD 7.6 — 0 violations
- SpotBugs 4.8 — 0 BugInstances
- Compile — clean

---

## 6. Known limitations retained (deferred by CTO's own MED/LOW priority list — no new debt)

All of these were carried forward from the WP-001 first-review known-limitations list; the CTO
categorised them as MED/LOW and did not require closure this round.

| # | Item | Status |
|---|---|---|
| 4 | Actuator sensitive endpoints not covered by explicit auth policy | Documented in `docs/operations/auditor-and-actuator.md`; policy is deny-by-default via the exposure list; runbook provided for the day someone wants to expose `/prometheus` |
| 6 | No purge job for soft-deleted rows | Framework shipped (this delivery item #8); implementation deferred as CTO instructed |
| 7 | AuditorProvider returns empty for system-initiated writes | Documented (this delivery item #9); explicitly not "fixed" — the NULL is honest |

Nothing HIGH-priority remains open.

---

## 7. Verification steps for the CTO

1. `git fetch origin && git checkout claude/wp-001-final`
2. `mvn -B -ntp verify` on a machine with Docker → expect all gates + JaCoCo ≥ 80 % + integration tests green.
3. Startup verifications:
   - `SPRING_PROFILES_ACTIVE=prod JWT_SECRET=change-me mvn spring-boot:run` → **must fail** with a clear "placeholder" message.
   - `SPRING_PROFILES_ACTIVE=prod JWT_SECRET=<32+ random bytes> APP_CORS_ALLOWED_ORIGINS=https://ewos.example.com mvn spring-boot:run` → boots.
   - `SPRING_PROFILES_ACTIVE=prod APP_CORS_ALLOWED_ORIGINS='*' JWT_SECRET=<strong>` → **must fail** on CORS wildcard.
4. Rate limit: hammer `POST /api/v1/auth/login` with wrong password from one client → after 60 requests in a minute, expect `429` with `Retry-After`.
5. Account lockout: 5 wrong passwords for the same user → 6th request returns `423 Locked` with `locked_until` in the audit trail.
6. Actuator: hit `/actuator/env` in `prod` → `404`. Hit `/actuator/health` → `200 UP`.

---

## 8. Verdict I need from you

- **FINAL APPROVED** — WP-001 complete; I proceed to package WP-002 (User Management) for review.
- **CONDITIONS REMAIN** — please list; I address on this branch, re-verify, resubmit.

I will not begin WP-002 until Final Approval is issued.
