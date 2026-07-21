# ADR-0004: CORS configuration — data-driven allow-list, deny-by-default in production

- **Status:** Accepted
- **Date:** 2026-07-14
- **Sprint:** 8.1 follow-up
- **Authors:** Backend team

## Context

The API is consumed from the browser (Sprint 8.1 handover for the frontend team). Until now,
`SecurityConfig.cors(Customizer.withDefaults())` was called but no `CorsConfigurationSource` bean
was registered — every browser-origin request was therefore rejected before it reached the
controller. The frontend was working around this with a Vite proxy.

We need production-ready CORS that:

1. Works for the browser without a proxy.
2. Is per-environment configurable — dev must allow local Vite / CRA origins; production must
   allow only whitelisted origins.
3. Never falls open in production, even by accident.
4. Interoperates with the JWT `Authorization` header the frontend already sends.

## Decision

### 1. `CorsConfigurationSource` is provided by a dedicated `CorsConfig` bean

`com.ewos.security.cors.CorsConfig` (marked `final`) reads `CorsProperties` and returns a
`UrlBasedCorsConfigurationSource` that Spring Security consumes via
`http.cors(c -> c.configurationSource(source))` in `SecurityConfig`. No changes to the auth flow,
error envelope, or security filter chain.

### 2. Configuration is data-driven — one property block, per-profile overrides

`app.cors.*` binds to `CorsProperties`:

| Property | Default | Purpose |
|---|---|---|
| `allowedOrigins` | (profile-supplied) | Exact-match origins |
| `allowedOriginPatterns` | empty | Spring's pattern origins (regex-like) |
| `allowedMethods` | GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD | Preflight method allow-list |
| `allowedHeaders` | Authorization, Content-Type, Accept, Origin, X-Request-ID, X-Requested-With | Preflight header allow-list |
| `exposedHeaders` | X-Request-ID, Location | Response headers the browser may read |
| `allowCredentials` | `true` | Required for the JWT `Authorization` header |
| `maxAge` | 1h | Preflight cache |
| `pathPatterns` | `/**` | Which paths the config applies to |

- **dev**: `http://localhost:5173`, `http://127.0.0.1:5173`, `http://localhost:3000`, `http://127.0.0.1:3000`.
- **test**: same Vite / CRA origins so integration tests hitting a preflight succeed.
- **prod**: `${APP_CORS_ALLOWED_ORIGINS:}` — **empty by default**. Operators must set the env var explicitly. Deny-by-default.

### 3. Wildcards are rejected in the `prod` profile

The bean's constructor validates configuration and throws `IllegalStateException` at startup if:

- The `prod` profile is active AND `allowedOrigins` (or `allowedOriginPatterns`) contains `*`.
- `allowCredentials=true` is combined with a wildcard, regardless of profile. Browsers reject this
  by CORS spec — we fail fast rather than at request time.

Non-prod profiles log a warning when `*` is used (with `allowCredentials=false`) — visible smell,
not a hard fail, so a developer can explore quickly.

### 4. Deny-by-default in production, warn if empty

If the `prod` profile is active with no origins configured, the app boots but a warning is logged
and cross-origin requests are rejected — safer than silently opening.

## Consequences

**Good**
- The frontend can hit the API directly from `http://localhost:5173` without a proxy in dev.
- Production explicitly enumerates the origins it trusts — the config is auditable via
  `APP_CORS_ALLOWED_ORIGINS`.
- The wildcard-with-credentials pitfall (which browsers reject anyway) fails fast at boot with a
  clear error message instead of at request time with a confusing browser error.
- `X-Request-ID` is exposed to the browser so the frontend can surface the correlation id in bug
  reports.

**Neutral**
- Adds one new `@Configuration` class + one `@ConfigurationProperties` binding — same pattern as
  `JwtProperties`, `PasswordPolicyProperties`, `BootstrapProperties`.
- Preflight `OPTIONS` responses are cached for 1 hour by default — long enough to reduce chatter,
  short enough that changing the allow-list doesn't require a browser restart.

**Bad / debts explicitly accepted**
- CI does not yet run a smoke test that boots the `prod` profile without `APP_CORS_ALLOWED_ORIGINS`
  set — we rely on operators reading the deploy runbook. A follow-up smoke test in CI would close
  this gap.
- Rate-limiting / brute-force throttling for auth endpoints is still deferred; CORS makes the
  browser-facing surface real, and the throttling gap is now more visible.

## Alternatives considered

- **Hardcoded origins in `SecurityConfig`.** Rejected — every environment change would be a code
  change.
- **`.setAllowedOriginPatterns("*")` in dev.** Rejected — a developer could ship it to prod. We
  make the wildcard explicit, warn on it in dev, and reject it in prod.
- **CORS at the ingress / reverse-proxy layer.** Fine as an additional defense in depth; not a
  replacement — the app must still refuse unknown origins if the ingress is bypassed.

## Follow-ups

1. Add a CI smoke test that boots the `prod` profile with `APP_CORS_ALLOWED_ORIGINS` unset and
   asserts start-up succeeds with the "no origins configured" warning line.
2. When Sprint 6.1 wires tenant isolation, revisit whether CORS should be tenant-scoped (per
   subdomain) — for now every tenant shares the same allow-list.
3. Add rate limiting on `/api/v1/auth/*` so the newly-reachable browser surface can't be brute
   forced.
